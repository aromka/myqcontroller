/**
 *  MyQ HCH module
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
 **/

/* module paths - please add your own path for node_modules if not here already */
module.paths.push('/usr/lib/node_modules');
module.paths.push('/usr/local/lib/node_modules');

var exports = module.exports = new function () {
    var
        app = null,
        module = null,
        config = {},
        callback = null,
        failed = false,
        https = require('https'),
        request = require('request').defaults({
            jar: true,
            encoding: 'utf8',
            followRedirect: true,
            followAllRedirects: true
        }),
        myQAppId = 'NWknvuBd7LoFHfXmKNMBcgajXtZEgKUh4V7WNzMidrpUUluDpVYVZx+xT4PCM5Kx',
        devices = [],
        // recover timer for re-sync - if initial recover request failed
        tmrRecover = null,
        // myq state polling timer
        tmrRefresh = 0,

        // initialization of cookies
        doInit = function () {
            log('Initializing...');
            failed = false;

            // disable the automatic recovery
            if (tmrRecover) {
                clearTimeout(tmrRecover);
            }
            tmrRecover = null;

            if (config.securityToken) {
                doGetDevices();
                setTimeout(doRecover, 4 * 3600 * 1000); // refresh security tokens every 4 hours
            } else {
                setTimeout(doRecover, 5 * 60 * 1000); // something did not work right, recover 5 minutes later
            }
        },

        // recovering procedures
        doRecover = function () {
            if (failed) {
                return;
            }

            alert('Refreshing security tokens...');
            failed = true;

            //abort refreshes
            if (tmrRefresh) {
                clearTimeout(tmrRefresh);
            }
            tmrRefresh = null;

            //setup automatic recovery
            if (tmrRecover) {
                clearTimeout(tmrRecover);
            }
            tmrRecover = setTimeout(doRecover, 5 * 60 * 1000); // recover in 5 minutes if for some reason the tokens are not refreshed

            app.refreshTokens(module);
        },

        /**
         * Get devices
         */
        doGetDevices = function () {

            var handleResponse = function (err, response, body) {

                if (!err && response.statusCode == 200) {
                    if (body) {
                        try {
                            var data = JSON.parse(body);
                            if ((data) && (data.Devices) && (data.Devices.length)) {
                                //cycle through each device
                                //log('Got ' + data.Devices.length + ' device(s)');
                                for (var d in data.Devices) {
                                    var dev = data.Devices[d];
                                    var device = {
                                        'id': dev.MyQDeviceId,
                                        'name': dev.TypeName.replace(' Opener', ''),
                                        'module': module,
                                        'type': dev.MyQDeviceTypeName.replace('VGDO', 'GarageDoorOpener'),
                                        'serial': dev.SerialNumber
                                    };
                                    for (var prop in dev.Attributes) {
                                        var attr = dev.Attributes[prop];
                                        doSetDeviceAttribute(device, attr.Name, attr.Value);
                                    }

                                    //Rename device with actual MYQ door name
                                    if (device['data-description'] !== '') {
                                        device.name = device['data-description'];
                                    }

                                    var existing = false;
                                    var notify = false;

                                    //we only push updates to other modules if there are any changes made
                                    for (var i in devices) {
                                        if (devices[i].id == device.id) {
                                            //found an existing device
                                            existing = true;
                                            if (JSON.stringify(devices[i]) != JSON.stringify(device)) {
                                                var attribute = '';
                                                var oldValue = '';
                                                var newValue = '';
                                                if (devices[i]['data-door'] != device['data-door']) {
                                                    attribute = 'data-door';
                                                    oldValue = devices[i]['data-door'];
                                                    newValue = device['data-door'];
                                                    notify = true;
                                                }
                                                //update the device
                                                devices[i] = device;
                                                //notify change
                                                if (notify) {
                                                    callback({
                                                        name: 'update',
                                                        data: {
                                                            device: device,
                                                            module: module,
                                                            attribute: attribute,
                                                            oldValue: oldValue,
                                                            newValue: newValue,
                                                            value: newValue,
                                                            description: 'Device "' + device.name + '" <' + device.id + '> changed its "' + attribute + '" value from "' + oldValue + '" to "' + newValue + '"'
                                                        }
                                                    });
                                                }
                                            }
                                            break;
                                        }
                                    }

                                    log('Existing? ' + existing + ' : ' + device.type + ' : ' + device['data-description']);
                                    if (!existing) {
                                        if (!existing && device.type != 'Gateway' && device['data-description'] != '') {
                                            devices.push(device);
                                            callback({
                                                name: 'discovery',
                                                module: module,
                                                data: {
                                                    device: device,
                                                    description: 'Discovered device "' + device.name + '" <' + device.id + '>'
                                                }
                                            });
                                        }
                                    }

                                    //custom delays depending on status
                                    var delay;
                                    switch (device['data-door']) {
                                        case 'open':
                                            delay = 5;
                                            break;
                                        case 'opening':
                                        case 'closing':
                                            delay = 1;
                                            break;
                                        default:
                                            delay = 10;
                                    }
                                }

                                tmrRefresh = setTimeout(doGetDevices, delay * 1000); //every ? seconds
                                return;
                            }
                        } catch (e) {
                            //reinitialize after an error
                            error('Error reading device list: ' + e);
                            doRecover();
                            return;
                        }
                    }
                }
                //reinitialize on error
                error('Error getting device list: ' + err);
                doRecover();
            };

            request
                .get({
                    url: 'https://myqexternal.myqdevice.com/api/UserDeviceDetails?appId=' + myQAppId +
                    '&securityToken=' + config.securityToken,
                    headers: {
                        'User-Agent': 'Chamberlain/2793 (iPhone; iOS 9.3; Scale/2.00)'
                    }
                }, handleResponse);
        },

        doSetDeviceAttribute = function (device, attribute, value) {

            switch (device.type) {
                case 'GarageDoorOpener':
                    if (attribute == 'doorstate') {
                        switch (value) {
                            case '1':
                            case '9':
                                value = 'open';
                                break;
                            case '2':
                                value = 'closed';
                                break;
                            case '3':
                                value = 'stopped';
                                break;
                            case '4':
                                value = 'opening';
                                break;
                            case '5':
                                value = 'closing';
                                break;
                            default:
                                value = 'unknown';
                        }
                    }
                    break;

                case 'Light':
                    break;
            }

            attribute = attribute
                .replace('doorstate', 'door')
                .replace('desc', 'description');

            var attr = 'data-' + attribute;
            //return true if the value changed
            if (device[attr] != value) {
                var oldValue = device[attr]
                device[attr] = value;
                if (attr == 'data-door') {
                    device['data-contact'] = value;
                }
                return {
                    attr: attr,
                    oldValue: oldValue,
                    newValue: value
                }
            }
            return false;
        },

        //log
        log = function (message) {
            callback({
                name: 'log',
                data: {
                    message: message
                }
            });
        },

        //alert
        alert = function (message) {
            callback({
                name: 'log',
                data: {
                    alert: message
                }
            });
        },

        //error
        error = function (message) {
            callback({
                name: 'log',
                data: {
                    error: message
                }
            });
        };

    /**
     *
     * @param _app
     * @param _module
     * @param _config
     * @param _callback
     * @returns {boolean}
     */
    this.start = function (_app, _module, _config, _callback) {
        if (_app && _module && _config && _callback) {
            app = _app;
            module = _module;
            config = _config;
            callback = _callback;
            doInit();
            return true;
        }
        return false;
    };

    /**
     *
     * @param deviceId
     * @param command
     * @param value
     */
    this.processCommand = function (deviceId, command, value) {
        log('Got command ' + command + ' with value ' + value);
        doProcessCommand(deviceId, command, value);
    };

    /**
     *
     * @returns {boolean}
     */
    this.failed = function () {
        return !!failed;
    };

}();
