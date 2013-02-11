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
package org.cowboycoders.ant.events;

import static org.junit.Assert.*;

import org.junit.Test;

public class BroadcastMesengerTest {

  @Test
  public void test() {
    BroadcastMessenger<Integer> messenger = new BroadcastMessenger<Integer>();
    for(int i=0; i < 10 ; i ++) {
      final int j = i;
      System.out.println(j);
      messenger.addBroadcastListener( new BroadcastListener<Integer>() {
        
        
        @Override
        public void receiveMessage(Integer message) {
          System.out.println(j + " Thread: " + Thread.currentThread().getId());
        }
        
      });
      
      
    }
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    messenger.sendMessage(5);
    messenger.sendMessage(5);
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

}
