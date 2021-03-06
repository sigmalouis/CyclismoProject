/**
 *     Copyright (c) 2012, Will Szumski
 *
 *     This file is part of formicidae.
 *
 *     formicidae is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     formicidae is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with formicidae.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cowboycoders.ant.messages;

import org.cowboycoders.ant.messages.Constants.DataElements;
import org.cowboycoders.ant.utils.ValidationUtils;

/**
 * Channel id common functionality sahred between classes
 * @author will
 *
 */
public class ChannelIdCompanion {
  
  /**
   * @param message Message methods affect
   */
  
  private static final int PAIRING_FLAG_MASK = 0x80;
  private static final int DEVICE_TYPE_MASK =  0x7f;
  private static final int MAX_DEVICE_TYPE = 127;
  private static final int MAX_TRANSMISSION_TYPE = 255;
  private static final int MAX_DEVICE_NUMBER = 65535;

  
  private StandardMessage message;
  
  public ChannelIdCompanion(StandardMessage message) {
    this.setMessage(message);
  }

  public StandardMessage getMessage() {
    return message;
  }

  private void setMessage(StandardMessage message) {
    this.message = message;
  }
  
  /**
  * @param deviceType to set
  * @throws ValidationException if out of limit
  */
 public void setDeviceType(int deviceType) throws ValidationException {
   ValidationUtils.maxMinValidator(0, MAX_DEVICE_TYPE, deviceType, 
       MessageExceptionFactory.createMaxMinExceptionProducable("deviceType")
       );
   StandardMessage message = getMessage(); 
   message.setPartialDataElement(DataElements.DEVICE_TYPE,deviceType,DEVICE_TYPE_MASK);
 }
 

/**
* @param setPairingFlag  to set
*/
 public void setPairingFlag(boolean setPairingFlag) {
   int flag = setPairingFlag ? 1 : 0;
   StandardMessage message = getMessage(); 
   message.setPartialDataElement(DataElements.DEVICE_TYPE,flag,PAIRING_FLAG_MASK);
 }



/**
* 
* @param transmissionType  to set
* @throws ValidationException if out of limit
* 
* */
 public void setTransmissionType(int transmissionType) throws ValidationException {
   ValidationUtils.maxMinValidator(0, MAX_TRANSMISSION_TYPE, transmissionType, 
       MessageExceptionFactory.createMaxMinExceptionProducable("transmissionType")
       );
   StandardMessage message = getMessage(); 
   message.setDataElement(DataElements.TRANSMISSION_TYPE, transmissionType);
   
 }

 /**
  * 
  * @param deviceNumber to set
  * @throws ValidationException if out of limit
  */
 public void setDeviceNumber(int deviceNumber) throws ValidationException {
   ValidationUtils.maxMinValidator(0, MAX_DEVICE_NUMBER, deviceNumber, 
       MessageExceptionFactory.createMaxMinExceptionProducable("deviceNumber")
       );
   StandardMessage message = getMessage(); 
   message.setDataElement(DataElements.DEVICE_NUMBER,deviceNumber);
 }
  

}
