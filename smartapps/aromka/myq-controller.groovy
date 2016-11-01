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

private getLocalServerURN() {
	return "urn:schemas-upnp-org:device:HomeCloudHubLocalServer:624"
}

preferences {
	page(name: "prefWelcome", title: "Connect to MyQ Controller")
    page(name: "prefModulesPrepare", title: "Home Cloud Hub Modules")
	page(name: "prefMyQ", title: "MyQ™ Integration")
	page(name: "prefMyQConfirm", title: "MyQ™ Integration")
}


/***********************************************************************/
/*                        INSTALLATION UI PAGES                        */
/***********************************************************************/
def prefWelcome(params) {

    state.ihch = [security: [:]]

    return dynamicPage(name: "prefWelcome", title: "Connect to MyQ Controller local server", nextPage: "prefModulesPrepare") {
        atomicState.hchLocalServerIp = null
        def hchLocalServerIp = null

        section("Configuration") {
            input("hchLocalServerIp", "text", title: "Enter the IP of your local server", required: true, defaultValue: "192.168.0.")
        }
    }
}

def prefModulesPrepare(params) {
    state.ihch.localServerIp = params.hchLocalServerIp
    
    if (doHCHLogin()) {
    	doMyQLogin(true, true)
		return prefMyQ()
	} else {
        return dynamicPage(name: "prefModulesPrepare",  title: "Error connecting to MyQ Controller local server") {
            section(){
                paragraph "Sorry, your local server does not seem to respond at ${state.ihch.localServerIp}."
            }
        }
    }
}

def prefMyQ() {
    return dynamicPage(name: "prefMyQ", title: "MyQ™ Integration", nextPage:"prefMyQConfirm", install: state.ihch.useMyQ) {
        section("MyQ Login Credentials"){
            input("myqUsername", "email", title: "Username", description: "Your MyQ™ login", required: true)
            input("myqPassword", "password", title: "Password", description: "Your MyQ™ password", required: true)
        }
        section("Permissions") {
            input("myqControllable", "bool", title: "Control MyQ", required: true, defaultValue: true)
        }
    }
}

def prefMyQConfirm() {
    if (doMyQLogin(true, true)) {
		return dynamicPage(name: "prefMyQConfirm", title: "MyQ™ Integration", nextPage:"prefModules") {
			section(){
				paragraph "Congratulations! You have successfully connected your MyQ™ system."
			}
    	}
	} else {
		return dynamicPage(name: "prefMyQConfirm",  title: "MyQ™ Integration") {
			section(){
				paragraph "Sorry, the credentials you provided for MyQ™ are invalid. Please go back and try again."
			}
        }
    }
}


/***********************************************************************/
/*                           LOGIN PROCEDURES                          */
/***********************************************************************/
/* Login to Home Cloud Hub                                             */
/***********************************************************************/
private doHCHLogin() {
    atomicState.hchPong = false

    log.trace "Pinging local server at " + state.ihch.localServerIp
    sendLocalServerCommand state.ihch.localServerIp, "ping", ""

    def cnt = 50
    def hchPong = false
    while (cnt--) {
        pause(200)
        hchPong = atomicState.hchPong
        if (hchPong) {
            return true
        }
    }
    return false
}

/***********************************************************************/
/* Login to MyQ                                                        */
/***********************************************************************/
def doMyQLogin(installing, force) {
	def module_name = 'myq';

    // if cookies haven't expired and unless we need to force a login, we report all is pink
    if (!installing && !force && state.hch.security[module_name] && state.hch.security[module_name].connected && (state.hch.security[module_name].expires > now())) {
		log.info "Reusing previously login for MyQ"
		return true;
    }

    // setup our security descriptor
    def hch = (installing ? state.ihch : state.hch)
    hch.useMyQ = false
    hch.security[module_name] = [
    	'enabled': !!(settings.myqUsername || settings.myqPassword),
        'controllable': settings.myqControllable,
        'connected': false
    ]

    if (hch.security[module_name].enabled) {
    	log.info "Logging in to MyQ..."

        // perform the login, retrieve token
        def myQAppId = getMyQAppId()

        return httpGet("https://myqexternal.myqdevice.com/Membership/ValidateUserWithCulture?appId=${myQAppId}&securityToken=null&username=${settings.myqUsername}&password=${settings.myqPassword}&culture=en") { response ->
			// check response, continue if 200 OK
       		if (response.status == 200) {
				if (response.data && response.data.SecurityToken) {
                    hch.security[module_name].securityToken = response.data.SecurityToken
                    hch.security[module_name].connected = now()
                	hch.security[module_name].expires = now() + 5000 //expires in 5 minutes
					log.info "Successfully connected to MyQ"
                    hch.useMyQ = true
                	return true;
                }
			}
            return false;
 		}
    } else {
		return true;
	}
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
    state.installed = true    
    // get the installing hch state
    state.hch = state.ihch

	// login to myq
   	doMyQLogin(false, false)

    // initialize the local server
    sendLocalServerCommand state.hch.localServerIp, "init", [
        server: getHubLanEndpoint(),
        modules: state.hch.security
    ]

    // listen to LAN incoming messages
    subscribe(location, null, lanEventHandler, [filterEvents:false])
}


private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}


/***********************************************************************/
/*                    SMARTTHINGS EVENT HANDLERS                       */
/***********************************************************************/
def shmHandler(evt) {
	def shmState = location.currentState("alarmSystemStatus")?.value;
    log.info "Received notification of SmartThings Home Monitor having changed status to ${shmState}"
}

def modeChangeHandler(event) {
    if (event.name == 'mode') {
        log.info "Received notification of Location Mode having changed to ${event.value}"
	}
}

def lanEventHandler(evt) {
    def description = evt.description
    def hub = evt?.hubId
	def parsedEvent = parseLanMessage(description)
	
	// discovery
	if (parsedEvent.ssdpTerm && parsedEvent.ssdpTerm.contains(getLocalServerURN())) {
        atomicState.hchLocalServerIp = convertHexToIP(parsedEvent.networkAddress)
	}
    
    // ping response
    if (parsedEvent.data && parsedEvent.data.service && (parsedEvent.data.service == "hch")) {
	    def msg = parsedEvent.data
        if (msg.result == "pong") {
        	//log in successful to local server
            log.info "Successfully contacted local server"
			atomicState.hchPong = true
        }   	
    }

    if (parsedEvent.data && parsedEvent.data.event) {
        switch (parsedEvent.data.event) {
        	case "init":
                sendLocalServerCommand state.hch.localServerIp, "init", [
                            server: getHubLanEndpoint(),
                            modules: processSecurity([module: parsedEvent.data.data])
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


private getHubLanEndpoint() {
	def server = [:]
	location.hubs.each {
	    //look for enabled modules
        def ip = it?.localIP
        def port = it?.localSrvPortTCP
        if (ip && port) {
        	log.trace "Found local endpoint at ${ip}:${port}"
        	server.ip = ip
            server.port = port
            return server
        }
    }
    return server
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
    def deviceModule = data?.module
    def deviceName = data?.name.capitalize()
    def deviceType = data?.type
    def description = data?.description
    if (description) {
    	log.info 'Received event: ' + description
    } else {
    	log.info "Received ${eventName} event for module ${deviceModule}, device ${deviceName}, value ${eventValue}, data: $data"
    }

	// see if the specified device exists and create it if it does not exist
    def deviceDNI = (deviceModule + '-' + deviceId).toLowerCase();
    def device = getChildDevice(deviceDNI)
    if(!device) {

       log.info "Got a new device, lets create it"

    	//build the device type
        def deviceHandler = null;

        //support for various modules
        if (deviceModule == "myq") {
            deviceHandler = 'MyQ ' + deviceType.replace('GarageDoorOpener', 'Garage Door').replace('-', ' ').split()*.capitalize().join(' ')
        }

        if (deviceHandler) {
        	// we have a valid device type, create it
            try {
        		device = addChildDevice("ady624", deviceHandler, deviceDNI, null, [label: deviceName])
        		device.sendEvent(name: 'id', value: deviceId);
        		device.sendEvent(name: 'module', value: deviceModule);
        		device.sendEvent(name: 'type', value: deviceType);
            } catch(e) {
            	log.info "Home Cloud Hub discovered a device that is not yet supported by your hub. Please find and install the [${deviceHandler}] device handler from https://github.com/ady624/HomeCloudHub/tree/master/devicetypes/ady624"
            	log.info "If the repository is missing the [${deviceHandler}] device handler, please provide the device data to the author of this software so he can add it. Thank you. Device data is [${data}]"
            }
        }
    }

    if (device) {

        log.info "Device already exists"

    	// we have a valid device that existed or was just created, now set the state
        for(param in data) {
            def key = param.key
        	def value = param.value
        	if ((key.size() > 5) && (key.substring(0, 5) == 'data-')) {
            	key = key.substring(5);
                def oldValue = device.currentValue(key);
                if (oldValue != value) {
					device.sendEvent(name: key, value: value);
                    //list of capabilities
                    //http://docs.smartthings.com/en/latest/capabilities-reference.html
            	}
        	}
    	}
    }
}

private processSecurity(data) {
	if (!data) {
    	data = params
    }
    
	def module = data?.module
    if (module) {
		log.info "Received request for security tokens for module ${module}"
    } else {
		log.info "Received request for security tokens for all modules"
    }

	// we are provided an endpoint to which to send command requests
	state.hch.security.each {
	    //look for enabled modules
        def name = it?.key
        def config = it?.value
        if (config.enabled && ((name == module) || !module)) {
        	switch (name) {
            	case "myq":
                	doMyQLogin(false, !module)
                    break
            }
	        config.age = (config.connected ? (now() - config.connected) / 1000 : null)
        }
    }

    log.info "Providing security tokens to Home Cloud Hub"
    if (module) {
        //we only requested one module for refresh
        def sl = [:]
        if (state.hch.security[module]) {
        	sl[module] = state.hch.security[module];
        }
        return sl;
    } else {
    	return state.hch.security;
    }
}


/***********************************************************************/
/*                       EXTERNAL COMMAND PROXY                        */
/***********************************************************************/
def proxyCommand(device, command, value) {
	// child devices will use us to proxy things over to the homecloudhub.com service
	def module = device.currentValue('module')
    try {
        return "cmd_${module}"(device, command, value, false)
    } catch(e) {
    	return "Error proxying command: ${e}"
    }
}


/***********************************************************************/
/*                          MYQ MODULE COMMANDS                        */
/***********************************************************************/
def cmd_myq(device, command, value, retry) {

	// are we allowed to use MyQ?
   	def module_name = "myq"
	if (!state.hch.useMyQ || !(state.hch.security && state.hch.security[module_name] && state.hch.security[module_name].controllable)) {
    	//we are either not using this module or we can't controll it
    	return "No permission to control MyQ"
    }

	if (!doMyQLogin(false, retry)) {
    	log.error "Failed sending command to MyQ because we could not connect"
    }

	// using the cookies, retrieve the auth tokens
    def attrName = null
    def attrValue = null
    switch (device.currentValue("type")) {
    	case "GarageDoorOpener":
        	switch (command) {
            	case "open":
                	attrName = "desireddoorstate"
                	attrValue = 1
                	break;
            	case "close":
                	attrName = "desireddoorstate"
                	attrValue = 0
                	break;
            }
        	break;
    }

    def result = false
    def message = ""
    if (attrName && attrValue != null) {
    	try {
            result = httpPutJson([
                uri: "https://myqexternal.myqdevice.com/api/v4/deviceAttribute/putDeviceAttribute?appId=" + getMyQAppId() + "&securityToken=${state.hch.security[module_name].securityToken}",
                headers: [
                    "User-Agent": "Chamberlain/2793 (iPhone; iOS 9.3; Scale/2.00)"
                ],
                body: [
					ApplicationId: getMyQAppId(),
					SecurityToken: state.hch.security[module_name].securityToken,
                    MyQDeviceId: device.currentValue('id'),
					AttributeName: attrName,
                    AttributeValue: attrValue
                ]
            ]) { response ->
                //check response, continue if 200 OK
                message = response.data
                if ((response.status == 200) && response.data && (response.data.ReturnCode == 0)) {
                	return true
                }
                return false
            }
            if (result) {
            	return "Successfully sent command to MyQ: device [${device}] command [${command}] value [${value}] result [${message}]"
            } else {
            	//if we failed and this was already a retry, give up
            	if (retry) {
		            return "Failed sending command to MyQ: ${message}"
                }
            	//we failed the first time, let's retry
                return "cmd_${module_name}"(device, command, value, true)
            }
		} catch(e) {
    		message = "Failed sending command to MyQ: ${e}"
        }
    }
}