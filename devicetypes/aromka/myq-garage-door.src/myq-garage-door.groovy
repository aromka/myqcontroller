/**
 *  MyQ Garage Door
 *
 *  Copyright 2016 Roman Alexeev
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
	definition (name: "MyQ Garage Door", namespace: "aromka", author: "aromka", oauth: true) {
        capability "Refresh"
        capability "Polling"
        capability "Garage Door Control"
        capability "Contact Sensor"
        attribute "id", "string"
        attribute "module", "string"
        attribute "type", "string"
	}

	// UI tile definitions
	tiles(scale: 2) {
		standardTile("door", "device.door", width: 2, height: 2) {
            state "open", label: '${name}', icon: "st.doors.garage.garage-open", backgroundColor: "#ffa81e", canChangeIcon: true, canChangeBackground: true
            state "closed", label: '${name}', icon: "st.doors.garage.garage-closed", backgroundColor: "#79b821", canChangeIcon: true, canChangeBackground: true
        }

		multiAttributeTile(name:"details", type:"generic", width:6, height:4) {
			tileAttribute("device.door", key: "PRIMARY_CONTROL") {
      			attributeState "open", label: '${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#ffa81e", action: "close", nextState:"closing"
      			attributeState "closing", label: '${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#ffa81e", action: "open", nextState:"closed"
      			attributeState "closed", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#79b821", action: "open", nextState:"opening"
      			attributeState "opening", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#79b821", action: "close", nextState:"open"
    		}
		}

        main(["door"])
        details(["details"])
	}
}

// parse events into attributes
def parse(String description) {
}

def open() {
	parent.proxyCommand(device, 'desireddoorstate', 1);
}

def close() {
	parent.proxyCommand(device, 'desireddoorstate', 0);
}