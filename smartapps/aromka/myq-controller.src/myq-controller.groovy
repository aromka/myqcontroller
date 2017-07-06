/**
 *  MyQ Controller
 *
 *  Copyright 2016 Roman Alexeev
 *
 *  NOTE: This application requires a local server connected to the same network as your SmartThings hub.
 *        Find more info at https://github.com/aromka/MyQController
 *
 *  Based on the work of https://github.com/ady624/HomeCloudHub
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
**/

definition(
    name: "MyQ Controller",
    namespace: "aromka",
    singleInstance: true,
    author: "aromka",
    description: "Provides integration with MyQ Garage doors and switches",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png",
    oauth: true)

private getMyQAppId() {
    return 'NWknvuBd7LoFHfXmKNMBcgajXtZEgKUh4V7WNzMidrpUUluDpVYVZx+xT4PCM5Kx'
}

preferences {
	page(name: "prefWelcome")
	page(name: "prefMyQValidate")
}


/***********************************************************************/
/*                        INSTALLATION UI PAGES                        */
/***********************************************************************/
def prefWelcome() {

    dynamicPage(name: "prefWelcome", title: "MyQ™ Integration", uninstall: true, nextPage: "prefMyQValidate") {

        section("Local Server Settings") {
            input("localServerIp", "text", title: "Enter the IP of your local server", required: true, defaultValue: "192.168.0.")
        }

        section("MyQ Credentials"){
            input("myqUsername", "email", title: "Username", description: "Your MyQ™ login", required: true)
            input("myqPassword", "password", title: "Password", description: "Your MyQ™ password", required: true)
        }
    }
}

def prefMyQValidate() {

    atomicState.security = [:]

	if (doLocalLogin()) {
        if (doMyQLogin(true, true)) {
            dynamicPage(name: "prefMyQValidate", title: "MyQ™ Integration Completed", install: true) {
                section(){
                    paragraph "Congratulations! You have successfully connected your MyQ™ system."
                }
            }
        } else {
            dynamicPage(name: "prefMyQValidate",  title: "MyQ™ Integration Error") {
                section(){
                    paragraph "Sorry, the credentials you provided for MyQ™ are invalid. Please go back and try again."
                }
            }
        }
    } else {
	    dynamicPage(name: "prefMyQValidate",  title: "MyQ™ Integration Error") {
            section(){
                paragraph "Sorry, your local server does not seem to respond at ${settings.localServerIp}."
            }
        }
    }
}


/***********************************************************************/
/*                           LOGIN PROCEDURES                          */
/***********************************************************************/
/* Login to local server                                               */
/***********************************************************************/
private doLocalLogin() {

	if(!atomicState.subscribed) {
		subscribe(location, null, lanEventHandler, [filterEvents:false])
		atomicState.subscribed = true
    }

    atomicState.pong = false

    log.trace "Pinging local server at " + settings.localServerIp
    sendLocalServerCommand settings.localServerIp, "ping", ""

    def cnt = 50
    def pong = false
    while (cnt--) {
        pause(200)
        pong = atomicState.pong
        log.trace "Pong: " + atomicState.pong
        if (pong) {
            return true
        }
    }
    return false
}

/***********************************************************************/
/* Login to MyQ                                                        */
/***********************************************************************/
def doMyQLogin(installing, force) {

    // if cookies haven't expired and unless we need to force a login, we report all is pink
    if (!installing && !force && atomicState.security && atomicState.security.connected && atomicState.security.expires > now()) {
		log.info "Reusing previously login for MyQ"
		return true;
    }

    // setup our security descriptor
    atomicState.security = [
        'securityToken': null,
    	'enabled': !!(settings.myqUsername && settings.myqPassword),
        'connected': 0
    ]

    if (!atomicState.security.enabled) {
        log.info "Missing MyQ credentials"
        return false;
    }

    log.info "Logging in to MyQ... "

    // perform the login, retrieve token
    def result = false
    try {
        result = httpPost([
            uri: "https://myqexternal.myqdevice.com",
            path: "/api/v4/User/Validate",
            headers: [
                "User-Agent": "Chamberlain/3.73",
                "BrandId": "2",
                 "ApiVersion": "4.1",
                 "Culture": "en",
                 "MyQApplicationId": getMyQAppId()
            ],
            body: [
                username: settings.myqUsername,
                password: settings.myqPassword
            ]
        ]) { response ->

            log.info "Login response code: " + response.status

            // check response, continue if 200 OK
            if (response.status == 200) {

                if (response.data && response.data.SecurityToken != null) {
                    def tempStateSecurity = atomicState.security
                    tempStateSecurity.securityToken = response.data.SecurityToken
                    tempStateSecurity.connected = now()
                    tempStateSecurity.expires = now() + 900000 // expire in 15 minutes
                    atomicState.security = tempStateSecurity
                    log.info "Successfully connected to MyQ"
                    return true;
                }
            }

            log.info "Response data: " + response.data
            return false;
       }
    } catch (SocketException e)	{
        log.debug "API Error: $e"
    }

	return result;
}


/***********************************************************************/
/*            INSTALL/UNINSTALL SUPPORTING PROCEDURES                  */
/***********************************************************************/
def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def uninstalled() {
}

def initialize() {

	log.info "Initializing MyQ controller..."

	// login to myq
   	doMyQLogin(false, false)

    // initialize the local server
    sendLocalServerCommand settings.localServerIp, "init", [
        security: atomicState.security
    ]

    // listen to LAN incoming messages
    subscribe(location, null, lanEventHandler, [filterEvents:false])
}

/***********************************************************************/
/*                    SMARTTHINGS EVENT HANDLERS                       */
/***********************************************************************/
def lanEventHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId
	def parsedEvent = parseLanMessage(description)

    // ping response
    if (parsedEvent.data && parsedEvent.data.service && (parsedEvent.data.service == "myq")) {
	    def msg = parsedEvent.data
        if (msg.result == "pong") {
        	//log in successful to local server
            log.info "Successfully contacted local server"
			atomicState.pong = true
        }
    }

    if (parsedEvent.data && parsedEvent.data.event) {
        switch (parsedEvent.data.event) {
        	case "init":
                sendLocalServerCommand settings.localServerIp, "init", [
                    security: processSecurity()
                ]
				break
        	case "event":
            	processEvent(parsedEvent.data.data);
                break
        }
    }
}

private sendLocalServerCommand(ip, command, payload) {
    sendHubCommand(new physicalgraph.device.HubAction(
        method: "GET",
        path: "/${command}",
        headers: [
            HOST: "${ip}:42457"
        ],
        query: payload ? [payload: groovy.json.JsonOutput.toJson(payload).bytes.encodeBase64()] : []
    ))
}


/***********************************************************************/
/*                      EXTERNAL EVENT MAPPINGS                        */
/***********************************************************************/
mappings {
    path("/event") {
        action: [
            GET: "processEvent",
            PUT: "processEvent"
        ]
    }
    path("/security") {
        action: [
            GET: "processSecurity"
        ]
    }
}

/***********************************************************************/
/*                      EXTERNAL EVENT HANDLERS                        */
/***********************************************************************/
private processEvent(data) {
	if (!data) {
    	data = params
    }

    def eventName = data?.event
    def eventValue = data?.value
    def deviceId = data?.id
    def deviceName = data?.name.capitalize()
    def deviceType = data?.type
    def description = data?.description

    if (description) {
    	log.info 'Received event: ' + description
    } else {
    	log.info "Received ${eventName} event for device ${deviceName}, value ${eventValue}, data: $data"
    }

	// see if the specified device exists and create it if it does not exist
    def deviceDNI = 'myq-' + deviceId;
    def device = getChildDevice(deviceDNI)
    if (!device) {

       log.info "Adding new device: " + deviceType +  ", ID: " + deviceDNI

    	//build the device type
        def deviceHandler = null;

        // support for various device types
        if (deviceType == "GarageDoorOpener") {
            deviceHandler = 'MyQ Garage Door'
        } else if (deviceType == "LampModule") {
        	deviceHandler = 'MyQ Switch'
        }

        if (deviceHandler) {

        	log.info "Looking for device handler: " + deviceHandler

        	// we have a valid device type, create it
            try {
        		device = addChildDevice("aromka", deviceHandler, deviceDNI, null, [label: deviceName])
        		device.sendEvent(name: 'id', value: deviceId);
        		device.sendEvent(name: 'type', value: deviceType);
            } catch(e) {
            	log.info "MyQ Controller discovered a device that is not yet supported by your hub. Please find and install the [${deviceHandler}] device handler from https://github.com/aromka/MyQController/tree/master/devicetypes/aromka"
            }
        }

    } else {
        log.info "Device already exists. ID: " + deviceDNI
    }

    // we have a valid device that existed or was just created, now set the state
    if (device) {
        for (param in data) {
            def key = param.key
        	def value = param.value
        	if ((key.size() > 5) && (key.substring(0, 5) == 'data-')) {
            	key = key.substring(5);
                def oldValue = device.currentValue(key);
                if (oldValue != value) {
					device.sendEvent(name: key, value: value);
                    // list of capabilities
                    // http://docs.smartthings.com/en/latest/capabilities-reference.html
            	}
        	}
    	}
    }
}

private processSecurity() {
	doMyQLogin(false, true)

    log.info "Providing security token to MyQ Controller"
    return atomicState.security;
}


/***********************************************************************/
/*                          MYQ COMMANDS                        */
/***********************************************************************/
def proxyCommand(device, command, value) {
    exec(device, command, value, false)
}

def exec(device, command, value, retry) {

	// get myq login
	if (!doMyQLogin(false, retry)) {
    	log.error "Failed sending command to MyQ because we could not connect"
    }

    def result = false
    def message = ""
    if (command && value != null) {
    	log.info "Setting device " + device.currentValue("type") + ": " + command + "=" + value
    	try {
            result = httpPutJson([
                uri: "https://myqexternal.myqdevice.com/api/v4/deviceAttribute/putDeviceAttribute?appId=" + getMyQAppId() + "&securityToken=${atomicState.security.securityToken}",
                headers: [
                    "User-Agent": "Chamberlain/3.73",
                    "BrandId": "2",
                    "ApiVersion": "4.1",
                    "Culture": "en",
                    "MyQApplicationId": getMyQAppId()
                ],
                body: [
					ApplicationId: getMyQAppId(),
					SecurityToken: atomicState.security.securityToken,
                    MyQDeviceId: device.currentValue('id'),
					AttributeName: command,
                    AttributeValue: value
                ]
            ]) { response ->
                //check response, continue if 200 OK
                message = response.data
                if ((response.status == 200) && response.data && (response.data.SecurityToken != null)) {
                	return true
                }
                return false
            }

            if (result) {
            	return "Successfully sent command to MyQ: device [${device}] command [${command}] value [${value}] result [${message}]"
            } else {
            	// if we failed and this was already a retry, give up
            	if (retry) {
		            return "Failed sending command to MyQ: ${message}"
                }

            	// we failed the first time, let's retry
                return exec(device, command, value, true)
            }
		} catch(e) {
    		message = "Failed sending command to MyQ: ${e}"
        }
    }
}