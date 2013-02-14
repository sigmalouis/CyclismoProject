/*
*    Copyright (c) 2013, Will Szumski
*    Copyright (c) 2013, Doug Szumski
*
*    This file is part of Cyclismo.
*
*    Cyclismo is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    Cyclismo is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with Cyclismo.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.cowboycoders.turbotrainers.bushido.brake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.cowboycoders.ant.AntLogger;
import org.cowboycoders.ant.Channel;
import org.cowboycoders.ant.NetworkKey;
import org.cowboycoders.ant.Node;
import org.cowboycoders.ant.events.BroadcastListener;
import org.cowboycoders.ant.events.MessageCondition;
import org.cowboycoders.ant.events.MessageConditionFactory;
import org.cowboycoders.ant.messages.ChannelMessage;
import org.cowboycoders.ant.messages.MessageId;
import org.cowboycoders.ant.messages.MessageMetaWrapper;
import org.cowboycoders.ant.messages.SlaveChannelType;
import org.cowboycoders.ant.messages.StandardMessage;
import org.cowboycoders.ant.messages.data.AcknowledgedDataMessage;
import org.cowboycoders.ant.messages.data.BroadcastDataMessage;
import org.cowboycoders.ant.messages.data.DataMessage;
import org.cowboycoders.ant.messages.responses.ResponseCode;
import org.cowboycoders.ant.utils.ArrayUtils;
import org.cowboycoders.ant.utils.ChannelMessageSender;
import org.cowboycoders.turbotrainers.AntTurboTrainer;
import org.cowboycoders.turbotrainers.TooFewAntChannelsAvailableException;
import org.cowboycoders.turbotrainers.TurboTrainerDataListener;
import org.cowboycoders.turbotrainers.bushido.headunit.BushidoButtonPressDescriptor.Button;
import org.cowboycoders.utils.IterationOperator;
import org.cowboycoders.utils.IterationUtils;


public class BushidoBrakeController extends AntTurboTrainer {
  
  public final static Logger LOGGER = Logger.getLogger(BushidoHeadunit.class .getName());
 
  public static final byte[] PACKET_ALIVE = BushidoHeadunit.padToDataLength(new int [] {0xac, 0x03, 0x02});
  
  public static final byte[] PACKET_RESET_ODOMETER = BushidoHeadunit.padToDataLength(new int[] {0xac, 0x03, 0x01});
  
  public static final byte[] PACKET_DISCONNECT = BushidoHeadunit.padToDataLength(new int[] {0xac, 0x03});
  
  public static final byte[] PACKET_INIT_CONNECTION = BushidoHeadunit.padToDataLength(new int[] {0xac, 0x03,0x04});
  
  public static final byte[] PACKET_START_CYLING = BushidoHeadunit.padToDataLength(new int[] {0xac, 0x03,0x03});
  
  private static final long RESET_ODOMETER_TIMEOUT = TimeUnit.SECONDS.toNanos(5);
  
  private static final Byte[] PARTIAL_PACKET_CONNECTION_SUCCESSFUL = new Byte [] {(byte) 0xad,0x01,0x04};
  // this may not be NO_CONNECTION
  private static final Byte[] PARTIAL_PACKET_NO_CONNECTION = new Byte [] {(byte) 0xad,0x01,0x00};
  
  private static final MessageCondition CONDITION_CHANNEL_TX = 
      MessageConditionFactory.newResponseCondition(MessageId.EVENT, ResponseCode.EVENT_TX);
  
  private ArrayList<BroadcastListener<? extends ChannelMessage>> listeners = 
      new ArrayList<BroadcastListener<? extends ChannelMessage>>();
  
  private Node node;
  private NetworkKey key;
  private Channel channel;
  private ExecutorService channelExecutorService = Executors.newSingleThreadExecutor();
  private ChannelMessageSender channelMessageSender;


  private BushidoData model;
  
  /**
   * If locked should not send data packets;
   */
  private Lock responseLock = new ReentrantLock();
  
  /**
   * Weak set 
   */
  private Set<TurboTrainerDataListener> dataChangeListeners = Collections.newSetFromMap(new WeakHashMap<TurboTrainerDataListener,Boolean>());
  

  boolean distanceUpdated = false;
  
  private Lock requestPauseLock = new ReentrantLock();
  private boolean requestPauseInProgess = false;
  private Lock requestDataLock = new ReentrantLock();
  private boolean requestDataInProgess = false;
  private Runnable requestPauseCallback = new Runnable() {
    
    @Override
    public void run() {
      try {
        requestPauseLock.lock();
        requestPauseInProgess = false;
      } finally {
        requestPauseLock.unlock();
      } 
      
    }
    
  };
  
  private Runnable requestDataCallback = new Runnable() {

    @Override
    public void run() {
      try {
        requestDataLock.lock();
        requestDataInProgess = false;
      } finally {
        requestDataLock.unlock();
      }   
      
    }
    
  };


  private boolean respond = true;
  
  
  public class BushidoUpdatesListener implements BushidoInternalListener {
    
    private BushidoData data;
        
    /**
     * Only route responses through this member
     */
    private ChannelMessageSender channelSender;

    public BushidoUpdatesListener(BushidoData data, ChannelMessageSender channelSender) {
      this.data = data;
      this.channelSender = channelSender;
    }

    @Override
    public void onRequestData() {
      
      // We don't want thread's queueing up waiting to be serviced.
      // Subject to a race but we will respond to next request.
      try {
        requestDataLock.lock();
        if (requestDataInProgess) return;
        requestDataInProgess = true;
      } finally {
        requestDataLock.unlock();
      }
      
      byte [] bytes = null;
      synchronized(data) {
        bytes = data.getDataPacket();
      }
      
      channelSender.sendMessage(buildBroadcastMessage(bytes), requestDataCallback);
    }

    @Override
    public void onRequestKeepAlive() {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void onSpeedChange(final double speed) {
      synchronized(data) {
        data.setSpeed(speed);
      }
      synchronized (dataChangeListeners) {
        IterationUtils.operateOnAll(dataChangeListeners, new IterationOperator<TurboTrainerDataListener>() {
          @Override
          public void performOperation(TurboTrainerDataListener dcl) {
            dcl.onSpeedChange(speed);
          }
          
        });
      }
      
    }

    @Override
    public  void onPowerChange(final double power) {
      synchronized(data) {
        data.setPower(power);
      }
      synchronized (dataChangeListeners) {
        IterationUtils.operateOnAll(dataChangeListeners, new IterationOperator<TurboTrainerDataListener>() {
          @Override
          public void performOperation(TurboTrainerDataListener dcl) {
            dcl.onPowerChange(power);
          }
          
        });
      }
      
    }

    @Override
    public  void onCadenceChange(final double cadence) {
     synchronized(data) {
       data.setCadence(cadence);
     }
     synchronized (dataChangeListeners) {
       IterationUtils.operateOnAll(dataChangeListeners, new IterationOperator<TurboTrainerDataListener>() {
         @Override
         public void performOperation(TurboTrainerDataListener dcl) {
           dcl.onCadenceChange(cadence);
         }
         
       });
     }
    }

    @Override
    public void onDistanceChange(final double distance) {
      synchronized(data) {
        data.setDistance(distance);
        distanceUpdated = true;
        synchronized(this) {
          this.notifyAll();
        }
      }
      synchronized (dataChangeListeners) {
        IterationUtils.operateOnAll(dataChangeListeners, new IterationOperator<TurboTrainerDataListener>() {
          @Override
          public void performOperation(TurboTrainerDataListener dcl) {
            synchronized(data) {
              dcl.onDistanceChange(data.getCompensatedDistance());
            }
          }
          
        });
      }
    }

    @Override
    public void onHeartRateChange(final double heartRate) {
      synchronized(data) {
        data.setHearRate(heartRate);
      }
      synchronized (dataChangeListeners) {
        IterationUtils.operateOnAll(dataChangeListeners, new IterationOperator<TurboTrainerDataListener>() {
          @Override
          public void performOperation(TurboTrainerDataListener dcl) {
            dcl.onHeartRateChange(heartRate);
          }
          
        });
      }
    }

    @Override
    public void onRequestPauseStatus() {
      try {
        requestPauseLock.lock();
        if (requestPauseInProgess) return;
        requestPauseInProgess = true;
      } finally {
        requestPauseLock.unlock();
      }
      
        BroadcastDataMessage msg = buildBroadcastMessage(PACKET_ALIVE);
        channelSender.sendMessage(msg, requestPauseCallback);
    }
    


    @Override
    public synchronized void onButtonPressFinished(final BushidoButtonPressDescriptor descriptor) {
      synchronized (buttonPressListeners) {
        IterationUtils.operateOnAll(buttonPressListeners, new IterationOperator<BushidoButtonPressListener>() {
          @Override
          public void performOperation(BushidoButtonPressListener bpl) {
            bpl.onButtonPressFinished(descriptor);
            
          }
          
        });
      }
      
    }
    
    
    @Override
    public void onButtonPressActive(final BushidoButtonPressDescriptor descriptor) {
      synchronized (buttonPressListeners) {
        IterationUtils.operateOnAll(buttonPressListeners, new IterationOperator<BushidoButtonPressListener>() {
          @Override
          public void performOperation(BushidoButtonPressListener bpl) {
            bpl.onButtonPressActive(descriptor);
            
          }
          
        });
      }
      
    }


    
  };
  
  public static BroadcastDataMessage buildBroadcastMessage(byte [] data) {
    BroadcastDataMessage msg = new BroadcastDataMessage();
    msg.setData(data);
    return msg;
}
  
  /**
   * Stored in weak set, so keep a reference : no anonymous classes
   */
  public void registerButtonPressListener(BushidoButtonPressListener listener) {
    synchronized (buttonPressListeners) {
      buttonPressListeners.add(listener);
    }
  }
  
 
  public double getSlope () {
    synchronized (model) {
      return model.getSlope();
    }
  }
  
  public void setSlope (double slope) {
    synchronized (model) {
      model.setSlope(slope);
    }
  }
  
  public void incrementSlope(double value) {
    synchronized (model) {
      model.incrementSlope(value);
    }
  }
  
  public void decrementSlope(double value) {
    synchronized (model) {
      model.decrementSlope(value);
    }
  }
  
  public void incrementSlope() {
    incrementSlope(0.1);
  }

  public void decrementSlope() {
    decrementSlope(0.1);
  }
  
  /**
   * As a opposed to that based on artificial speed used to compensate for negative gradients 
   * @return
   */
  public double getRealDistance() {
    synchronized (model) {
      return model.getDistance();
    }
  }
 
  public BushidoHeadunit(Node node) {
    super(node);
    this.node = node;
    this.key = new NetworkKey(0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00);
    key.setName("N:PUBLIC");
  }
  
  /**
   * Call after start.
   * @param listener
   * @param clazz
   */
  private <V extends ChannelMessage> void registerChannelRxListener(BroadcastListener<V> listener, Class<V> clazz) {
    synchronized (listeners) {
      listeners.add(listener);
      channel.registerRxListener(listener, clazz);
    }
  }
  
  private <V extends ChannelMessage> void unregisterRxListener(BroadcastListener<? extends ChannelMessage> listener) {
    channel.removeRxListener(listener);
  }
  
  public void start() throws InterruptedException, TimeoutException {
    startConnection();
    resetOdometer();
    startCycling();
  }
  
  
  public void startConnection() throws InterruptedException, TimeoutException {
    node.start();
    node.setNetworkKey(0, key);
    channel = node.getFreeChannel();
    
    if (channel == null) {
      throw new TooFewAntChannelsAvailableException();
    }
    channel.setName("C:BUSHIDO");
    SlaveChannelType channelType = new SlaveChannelType();
    channel.assign("N:PUBLIC", channelType);
    
    channel.setId(0, 0x52, 0, false);
    
    channel.setFrequency(60);
    
    channel.setPeriod(4096);
    
    channel.setSearchTimeout(255);
    
    channel.open();
    
    channelMessageSender = new ChannelMessageSender() {

      @Override
      public void sendMessage(final ChannelMessage msg, final Runnable callback) {
        channelExecutorService.execute(new Runnable() {

          @Override
          public void run() {
            
            try {
              responseLock.lock();
                if (!respond) {
                  if (callback != null) {
                    callback.run();
                  }
                  return;
                }
            } finally {
              responseLock.unlock();
            }
            
            try {
              channel.sendAndWaitForAck(msg, CONDITION_CHANNEL_TX, 10L, TimeUnit.SECONDS, null, null);
            } catch (Exception e) {
              LOGGER.severe("Message send failed");
            }
            
            if(callback != null) {
              callback.run();
            }
            
          }});
        
      }

      @Override
      public void sendMessage(ChannelMessage msg) {
        sendMessage(msg,null);
        
      }
      
    };
    
    initConnection();    
    
    //startCycling();
    
    this.model = new BushidoData();
    BushidoUpdatesListener updatesListener = new BushidoUpdatesListener(model,this.getMessageSender());
    BushidoBroadcastDataListener dataListener = new BushidoBroadcastDataListener(updatesListener);
    BushidoInternalButtonPressListener buttonListener = new BushidoInternalButtonPressListener(updatesListener);
    this.registerChannelRxListener(dataListener, BroadcastDataMessage.class);
    this.registerChannelRxListener(buttonListener, AcknowledgedDataMessage.class);
  }
  
  public void startCycling() throws InterruptedException, TimeoutException {
    BroadcastDataMessage msg = new BroadcastDataMessage();
    msg.setData(BushidoHeadunit.PACKET_START_CYLING);
    channel.sendAndWaitForAck(msg, CONDITION_CHANNEL_TX, 10L, TimeUnit.SECONDS, null, null);
  }

  private void initConnection() throws InterruptedException, TimeoutException {
    BroadcastDataMessage msg = new BroadcastDataMessage();
    msg.setData(BushidoHeadunit.PACKET_INIT_CONNECTION);
    MessageCondition condition = new MessageCondition() {

      @Override
      public boolean test(StandardMessage msg) {
        if(!(msg instanceof DataMessage)) return false;
        DataMessage dataMessage = (DataMessage) msg;
        if(!ArrayUtils.arrayStartsWith(PARTIAL_PACKET_CONNECTION_SUCCESSFUL, dataMessage.getData())) return false;
        return true;
      }
      
    };
    sendAndRetry(msg,condition,20,1L,TimeUnit.SECONDS);
    
  }
  
  private void disconnect() throws InterruptedException, TimeoutException{
    BroadcastDataMessage msg = new BroadcastDataMessage();
    msg.setData(BushidoHeadunit.PACKET_DISCONNECT);
    MessageCondition condition = new MessageCondition() {

      @Override
      public boolean test(StandardMessage msg) {
        if(!(msg instanceof DataMessage)) return false;
        DataMessage dataMessage = (DataMessage) msg;
        if(!ArrayUtils.arrayStartsWith(PARTIAL_PACKET_NO_CONNECTION, dataMessage.getData())) return false;
        return true;
      }
      
    };
    MessageCondition chainedCondition = MessageConditionFactory.newChainedCondition(CONDITION_CHANNEL_TX,condition);
    sendAndRetry(msg,chainedCondition,20,1L,TimeUnit.SECONDS);
  }
  
  private StandardMessage sendAndRetry(final ChannelMessage msg, final MessageCondition condition,final int maxRetries, final long timeoutPerRetry, final TimeUnit timeoutUnit) throws InterruptedException, TimeoutException {
    StandardMessage response = null;
    int retries = 0;
    while (response == null) {
      try {
        response = channel.sendAndWaitForMessage(
            msg, 
            condition,
            timeoutPerRetry,timeoutUnit, 
            null,
            null
            );
      } catch  (TimeoutException e){
        LOGGER.finer("sendAndRetry : timeout");
        retries++;
        if (retries >= maxRetries) {
          throw e;
        }
      }
    }
    return response;
  }
  
  public ChannelMessageSender getMessageSender() {
    return channelMessageSender;
  }
  
  /**
   * If you don't call this after we start the headunit will remember the previously cycled distance
   * @throws TimeoutException 
   * @throws InterruptedException 
   */
  public void resetOdometer() throws InterruptedException, TimeoutException {
    
    try {
      responseLock.lock();
        respond = false;
    } finally {
      responseLock.unlock();
    }
      BroadcastDataMessage msg = new BroadcastDataMessage();
      msg.setData(BushidoHeadunit.PACKET_RESET_ODOMETER);
      channel.sendAndWaitForAck(msg, CONDITION_CHANNEL_TX, 10L, TimeUnit.SECONDS, null, null);
      long startTimeStamp = System.nanoTime();
      synchronized(model) {
        while(!distanceUpdated) {
          long currentTimestamp = System.nanoTime();
          long timeLeft = TIMEOUT_DISTANCE_UPDATED - (currentTimestamp - startTimeStamp);
          model.wait(TimeUnit.NANOSECONDS.toMillis(timeLeft));
        }
      }
      
      if (System.nanoTime() - startTimeStamp > TIMEOUT_DISTANCE_UPDATED) {
        throw new TimeoutException("timeout waiting for distance to be updated");
      }
      
      startTimeStamp = System.nanoTime();
      synchronized(model) {
        while (model.getDistance() > 0.000001) {
          channel.sendAndWaitForAck(msg, CONDITION_CHANNEL_TX, 10L, TimeUnit.SECONDS, null, null);
          long currentTimestamp = System.nanoTime();
          long timeLeft = RESET_ODOMETER_TIMEOUT - (currentTimestamp - startTimeStamp);
          if (timeLeft <= 0) {
            throw new TimeoutException("timeout waiting for distance to be reset");
          }
          model.wait(TimeUnit.NANOSECONDS.toMillis(timeLeft));
        }
      }
      
      try {
        responseLock.lock();
          respond = true;
      } finally {
        responseLock.unlock();
      }
      
  }

  public void stop() throws InterruptedException, TimeoutException {
    
    // make sure no-one sends and unpause which will cancel our stop request
    synchronized (listeners) {
      for (BroadcastListener<? extends ChannelMessage> listener : listeners) {
        unregisterRxListener(listener);
      }
    }
    disconnect();
    channel.close();
    channel.unassign();
    node.freeChannel(channel);
    // let external controiller stop node
    //node.stop();
  }
  
  public static byte[] padToDataLength (int [] array) {
    byte [] rtn = new byte[8];
    for (int i = 0 ; i< array.length ; i++) {
      rtn[i] = (byte)array[i];
    }
    return rtn;
  }

  @Override
  public void unregisterDataListener(TurboTrainerDataListener listener) {
    synchronized (dataChangeListeners) {
      dataChangeListeners.remove(listener);
    }
  }
  
  public MessageMetaWrapper<StandardMessage> send(ChannelMessage msg) {
    return channel.send(msg);
  }

  @Override
  public boolean supportsSpeed() {
    return true;
  }

  @Override
  public boolean supportsPower() {
    return true;
  }

  @Override
  public boolean supportsCadence() {
    return true;
  }

  @Override
  public boolean supportsHeartRate() {
    return false;
  }



}
