/**
 *  MyQ Switch
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
	definition (name: "MyQ Switch", namespace: "aromka", author: "aromka", oauth: true) {
        capability "Refresh"
        capability "Polling"
        capability "Switch"
        attribute "id", "string"
        attribute "module", "string"       
        attribute "type", "string"
	}

	// UI tile definitions
	tiles(scale: 2) {
		standardTile("switch", "device.switch", width: 2, height: 2,  decoration: "flat") {
			state "off", label: '${currentValue}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", defaultState: true
			state "on", label: '${currentValue}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"

        }

        standardTile("details", "device.switch", width: 6, height: 4,  decoration: "flat") {
			state "off", label: '${currentValue}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", defaultState: true
			state "on", label: '${currentValue}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"

        }

        main(["switch"])
        details(["details"])
	}
}

def parse(String description) {
}

def on() {
	log.trace "on()"
    parent.proxyCommand(device, 'desiredlightstate', 1);
}

def off() {
	log.trace "off()"
    parent.proxyCommand(device, 'desiredlightstate', 0);
}