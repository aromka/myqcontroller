/**
 *  MyQ Service
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
 **/

/* module paths - please add your own path for node_modules if not here already */
module.paths.push('/usr/lib/node_modules');
module.paths.push('/usr/local/lib/node_modules');

var exports = module.exports = new function () {
    var
        app = null,
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
        tmrRefresh = null;

    // initialization of cookies
    function doInit() {
        console.log(getTimestamp() + 'Initializing...');
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
    }

    // recovering procedures
    function doRecover() {
        if (failed) {
            return;
        }

        console.log(getTimestamp() + 'Refreshing security tokens...');
        failed = true;
        if (tmrRefresh) {
            clearTimeout(tmrRefresh);
        }
        tmrRefresh = null;

        //setup automatic recovery
        if (tmrRecover) {
            clearTimeout(tmrRecover);
        }
        tmrRecover = setTimeout(doRecover, 5 * 60 * 1000); // recover in 5 minutes if for some reason the tokens are not refreshed

        app.refreshTokens();
    }

    /**
     * Get devices
     */
    function doGetDevices() {

        var handleResponse = function (err, response, body) {

            if (!err && response.statusCode == 200) {
                if (body) {
                    try {
                        var data = JSON.parse(body);
                        if ((data) && (data.Devices) && (data.Devices.length)) {

                            // cycle through each device
                            for (var d in data.Devices) {

                                var dev = data.Devices[d],
                                    device = {
                                        'id': dev.MyQDeviceId,
                                        'name': dev.TypeName.replace(' Opener', ''),
                                        'type': dev.MyQDeviceTypeName.replace('VGDO', 'GarageDoorOpener'),
                                        'serial': dev.SerialNumber
                                    };

                                // if not switch or door - skip this
                                if (['GarageDoorOpener', 'LampModule'].indexOf(device.type) === -1) {
                                    continue;
                                }

                                // set device attributes
                                for (var prop in dev.Attributes) {
                                    var attr = dev.Attributes[prop];
                                    doSetDeviceAttribute(device, attr.Name, attr.Value);
                                }

                                // Rename device with actual MYQ door name
                                if (device['data-description'] !== '') {
                                    device.name = device['data-description'];
                                }

                                var existing = false;
                                var notify = false;

                                // we only push updates if there are any changes made
                                for (var i in devices) {

                                    if (devices[i].id == device.id) {
                                        // found an existing device
                                        existing = true;
                                        if (JSON.stringify(devices[i]) != JSON.stringify(device)) {
                                            var attribute = '',
                                                oldValue = '',
                                                newValue = '';

                                            if (devices[i]['data-door'] != device['data-door']) {
                                                attribute = 'data-door';
                                                oldValue = devices[i]['data-door'];
                                                newValue = device['data-door'];
                                                notify = true;
                                            } else if (devices[i]['data-switch'] != device['data-switch']) {
                                                attribute = 'data-switch';
                                                oldValue = devices[i]['data-switch'];
                                                newValue = device['data-switch'];
                                                notify = true;
                                            }

                                            // update the device
                                            devices[i] = device;

                                            // notify change
                                            if (notify) {
                                                callback({
                                                    name: 'update',
                                                    data: {
                                                        device: device,
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

                                if (!existing && device.type !== 'Gateway' && device['data-description'] !== '') {
                                    devices.push(device);
                                    callback({
                                        name: 'discovery',
                                        data: {
                                            device: device,
                                            description: 'Discovered device "' + device.name + '" <' + device.id + '>'
                                        }
                                    });
                                }
                            }

                            tmrRefresh = setTimeout(doGetDevices, 5 * 1000); // every 5 seconds
                            return;
                        }
                    } catch (e) {
                        // reinitialize after an error
                        console.error(getTimestamp() + 'Error reading device list: ' + e);
                        doRecover();
                        return;
                    }
                }
            }
            
            //reinitialize on error
            console.error(getTimestamp() + 'Error getting device list: ' + err);
            doRecover();
        };

        // get devices from myq server
        request
            .get({
                url: 'https://myqexternal.myqdevice.com/api/UserDeviceDetails?appId=' + myQAppId +
                '&securityToken=' + config.securityToken,
                headers: {
                    'User-Agent': 'Chamberlain/2793 (iPhone; iOS 9.3; Scale/2.00)'
                }
            }, handleResponse);
    }

    /**
     * Set device attributes
     *
     * @param device
     * @param attribute
     * @param value
     * @returns {*}
     */
    function doSetDeviceAttribute(device, attribute, value) {

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

            case 'LampModule':
                if (attribute == 'lightstate') {
                    switch (value) {
                        case '0':
                            value = 'off';
                            break;
                        case '1':
                            value = 'on';
                            break;
                        default:
                            value = 'unknown';
                    }
                }
                break;
        }

        attribute = attribute
            .replace('doorstate', 'door')
            .replace('lightstate', 'switch')
            .replace('desc', 'description');

        var attr = 'data-' + attribute;

        // return true if the value changed
        if (device[attr] != value) {
            var oldValue = device[attr];
            device[attr] = value;

            if (attr == 'data-door') {
                device['data-contact'] = value;
            } else if (attr == 'data-light') {
                device['data-switch'] = value;
            }

            return {
                attr: attr,
                oldValue: oldValue,
                newValue: value
            }
        }
        return false;
    }

    /**
     * Get timestamp string for the log
     */
    function getTimestamp() {
        var dt = new Date(),
            pad = function(val) {
                return val < 10 ? '0' + val : val;
            };

        return '[' + pad(dt.getDate()) + '/' + pad(dt.getMonth()) + ' ' +
            pad(dt.getHours()) + ':' + pad(dt.getMinutes()) + ':' + pad(dt.getSeconds()) + '] ';
    }

    /**
     *
     * @param _app
     * @param _config
     * @param _callback
     * @returns {boolean}
     */
    this.start = function (_app, _config, _callback) {
        if (_app && _config && _callback) {
            app = _app;
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
