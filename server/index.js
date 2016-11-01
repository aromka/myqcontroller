/**
 *  MyQ Controller Application
 *
 *  Copyright 2016 Roman Alexeev
 *  Based on work of Adrian Caramaliu (HomeCloudHub)
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
module.paths.push('../.');

var app = new function () {

    //modules
    var app = this,
        configFile = __dirname + '/config/config.json',
        node = {
            'ssdp': require('node-ssdp'),
            'http': require('http'),
            'url': require('url'),
            'request': require('request'),
            'colors': require('colors'),
            'fs': require('fs')
        },
        config = {},
        modules = [],
        server = null;

    /**
     * Process events from modules
     */
    function doProcessEvent(module, event) {
        try {
            if (event) {
                //log support
                if (event.name == 'log') {
                    if (event.data) {
                        event.data.module = module;
                        log(event.data);
                    }
                } else if (event.data && event.data.device) {
                    var device = event.data.device;
                    if (device.id && device.name && device.type) {
                        try {
                            var data = {
                                module: module,
                                id: device.id,
                                name: device.name,
                                type: device.type,
                                event: event.name,
                                value: event.data.value
                            };
                            for (attr in device) {
                                if (attr.substr(0, 5) == 'data-') {
                                    data[attr] = device[attr];
                                }
                            }
                            log({
                                info: 'Sending event to SmartThings: ' + (event.data.description || '')
                            });
                            node.request.put({
                                    url: 'http://' + config.server.ip + ':' + config.server.port + '/event',
                                    headers: {
                                        'Content-Type': 'application/json'
                                    },
                                    json: true,
                                    body: {
                                        event: 'event',
                                        data: data
                                    }
                                },
                                function (err, response, body) {
                                    if (err) {
                                        log({
                                            error: 'Failed sending event: ' + err
                                        });
                                    }
                                });

                        } catch (e) {
                            log({
                                error: 'Error parsing event data: ' + e
                            });
                        }
                    }
                }
            }
        } catch (e) {
            error('Failed to send event to SmartThings: ' + e);
        }
    }

    function log(event) {
        var t = (new Date()).toLocaleString();
        if (event) {
            event.module = event.module || 'myq';
            if (event.info) {
                console.log(t + ' [' + node.colors.cyan(event.module) + '] ' + event.info);
            }
            if (event.message) {
                console.log(t + ' [' + node.colors.green(event.module) + '] ' + event.message);
            }
            if (event.alert) {
                console.log(t + ' [' + node.colors.yellow(event.module) + '] ' + event.alert);
            }
            if (event.error) {
                console.log(t + ' [' + node.colors.red(event.module) + '] ' + event.error);
            }
        } else {
            console.log('');
        }
    }

    /**
     * Process server request
     *
     * @param request
     * @param response
     */
    function doProcessRequest(request, response) {

        console.log('Server started');

        try {
            var urlp = node.url.parse(request.url, true),
                query = urlp.query,
                payload = null,
                module;

            if (query && query.payload) {
                payload = JSON.parse((new Buffer(query.payload, 'base64')).toString())
            }

            if (request.method == 'GET') {
                switch (urlp.pathname) {
                    case '/ping':
                        response.writeHead(202, {
                            'Content-Type': 'application/json'
                        });
                        response.write(JSON.stringify({
                            service: 'hch',
                            result: 'pong'
                        }));
                        response.end();
                        return;

                    case '/init':
                        console.log('Received init request');

                        if (payload && payload.server && payload.modules) {
                            response.writeHead(202, {
                                'Content-Type': 'application/json'
                            });
                            response.end();

                            if (payload.server.ip && payload.server.port) {
                                config.server = payload.server || config.server;
                                if (config.server.ip && config.server.port) {
                                    doSaveConfig();
                                }
                            }

                            for (module in payload.modules) {
                                var cfg = payload.modules[module];
                                app.startModule(module, cfg);
                            }

                        }
                        break;
                }
            }
        } catch (e) {
            console.log("ERROR: " + e);
        }

        response.writeHead(500, {});
        response.end();
    }

    /**
     * Load config file
     */
    function doLoadConfig() {

        node.fs.readFile(configFile, function read(err, data) {
            if (err) {
                console.error('Failed to load config.json file');
            }

            try {
                config.server = JSON.parse(data);
                if (config.server && config.server.ip && config.server.port) {
                    console.log('Retrieved config for server: ' + config.server.ip + ':' + config.server.port);

                    node
                        .request
                        .put({
                            url: 'http://' + config.server.ip + ':' + config.server.port,
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            json: true,
                            body: {
                                event: 'init'
                            }
                        });
                }
            } catch (e) {
                console.error('Failed reading config file: ' + e);
            }
        });
    }

    function doSaveConfig() {
        node.fs.writeFile(configFile, JSON.stringify(config.server, null, 4));
    }

    /**
     * Start the server
     */
    this.start = function () {

        console.log('==== MyQ Controller ====');

        config = {};

        var ssdpServer = new node.ssdp.Server();
        ssdpServer.addUSN('urn:schemas-upnp-org:device:MQCLocalServer:624');
        ssdpServer.start();

        server = node.http.createServer(doProcessRequest);
        server.listen(42457, '0.0.0.0'); // hchls - HCH Local Server

        // load the configuration from config file
        doLoadConfig();
    };

    /**
     * Start module
     *
     * @param module
     * @param config
     */
    this.startModule = function (module, config) {
        try {
            console.log('Starting module ' + module);

            var initial = !modules[module];
            modules[module] = modules[module] || require('./modules/' + module + '.js');
            modules[module].config = config;

            if (initial || modules[module].failed) {
                modules[module].start(app, module, config, function (event) {
                    doProcessEvent(module, event);
                });
            }
        } catch (e) {
            console.error('Error starting module: ' + e);
        }
    };

    /**
     * Refresh security tokens
     * @param module
     */
    this.refreshTokens = function (module) {

        try {
            if (config.server && config.server.ip && config.server.port) {
                node.request.put({
                    url: 'http://' + config.server.ip + ':' + config.server.port,
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    json: true,
                    body: {
                        event: 'init',
                        data: module
                    }
                });
            }
        } catch (e) {
        }
    };
};

// start the app
app.start();
