/**
 *  MyQ Garage Door
 *
 *  Copyright 2016 Adrian Caramaliu
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "MyQ Garage Door", namespace: "aromka", author: "Adrian Caramaliu", oauth: true) {
		capability "Actuator"
        capability "Door Control"
        capability "Contact Sensor"
        capability "Sensor"
        capability "Refresh"
        capability "Polling"
        capability "Switch"
        capability "Garage Door Control"
        attribute "id", "string"
        attribute "module", "string"       
        attribute "type", "string"
	}

    simulator {
	}

	// UI tile definitions
	tiles(scale: 2) {
		standardTile("door", "device.door", width: 2, height: 2) {
            state "open", label: '${name}', icon: "st.doors.garage.garage-open", backgroundColor: "#ffa81e", canChangeIcon: true, canChangeBackground: true
            state "closed", label: '${name}', icon: "st.doors.garage.garage-closed", backgroundColor: "#79b821", canChangeIcon: true, canChangeBackground: true
        }

		multiAttributeTile(name:"multi", type:"generic", width:6, height:4) {
			tileAttribute("device.door", key: "PRIMARY_CONTROL") {
      			attributeState "open", label: '${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#ffa81e", action: "close", nextState:"closing"
      			attributeState "closing", label: '${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#ffa81e", action: "open", nextState:"closed"
      			attributeState "closed", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#79b821", action: "open", nextState:"opening"
      			attributeState "opening", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#79b821", action: "close", nextState:"open"
    		}
    		tileAttribute("device.type", key: "SECONDARY_CONTROL") {
      			attributeState "default", label: '${currentValue}', icon:"st.unknown.unknown", backgroundColor:"#ffa81e", unit:""
			}
		}

		standardTile("id", "device.id", decoration: "flat", width: 6, height: 1, ) {
            state "default", label: '${currentValue}', backgroundColor: "#808080", icon:"st.contact.contact.open"
        }
        
        main(["door"])
        details(["id","multi"])
	}
}

// parse events into attributes
def parse(String description) {
}

def open() {
	log.trace parent.proxyCommand(device, 'open', '');
}

def close() {
	log.trace parent.proxyCommand(device, 'close', '');
}