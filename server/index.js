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

module.paths.push('/usr/lib/node_modules');
module.paths.push('/usr/local/lib/node_modules');
module.paths.push('../.');

var app = new function () {

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
        myq = null,
        config = {},
        server = null;

    /**
     * Process events
     */
    function doProcessEvent(event) {
        try {
            if (event) {
                // log support
                if (event.name == 'log') {
                    if (event.data) {
                        console.log(event.data);
                    }
                } else if (event.data && event.data.device) {
                    var device = event.data.device;
                    if (device.id && device.name && device.type) {
                        try {
                            var data = {
                                id: device.id,
                                name: device.name,
                                type: device.type,
                                event: event.name,
                                value: event.data.value
                            };
                            for (var attr in device) {
                                if (attr.substr(0, 5) == 'data-') {
                                    data[attr] = device[attr];
                                }
                            }
                            console.log('Sending event to SmartThings: ' + (event.data.description || ''));
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
                                        console.error('Failed sending event: ' + err);
                                    }
                                });

                        } catch (e) {
                            console.error('Error parsing event data: ' + e);
                        }
                    }
                }
            }
        } catch (e) {
            error('Failed to send event to SmartThings: ' + e);
        }
    }

    /**
     * Process server request
     *
     * @param request
     * @param response
     */
    function doProcessRequest(request, response) {

        try {
            var urlp = node.url.parse(request.url, true),
                query = urlp.query,
                payload = null;

            console.log('Handling request for: ' + urlp.pathname);

            if (query && query.payload) {
                payload = JSON.parse((new Buffer(query.payload, 'base64')).toString())
            }

            if (request.method == 'GET') {
                switch (urlp.pathname) {
                    case '/ping':
                        console.log('Getting ping...');
                        response.writeHead(202, {
                            'Content-Type': 'application/json'
                        });
                        response.write(JSON.stringify({
                            service: 'myq',
                            result: 'pong'
                        }));
                        response.end();
                        return;

                    case '/init':
                        console.log('Received init request');

                        if (payload && payload.server) {
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

                            app.start(payload.security);
                        }
                        break;
                }
            }
        } catch (e) {
            console.error("ERROR: " + e);
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
    this.init = function () {

        console.log('==== MyQ Controller ====');

        config = {};

        var ssdpServer = new node.ssdp.Server();
        ssdpServer.addUSN('urn:schemas-upnp-org:device:MQCLocalServer:624');
        ssdpServer.start();

        server = node.http.createServer(doProcessRequest);
        server.listen(42457, '0.0.0.0');

        // load the configuration from config file
        doLoadConfig();
    };

    /**
     * Start
     *
     * @param config
     */
    this.start = function (config) {
        try {
            var initial = !myq;
            myq = myq || require('./service/myq.js');
            myq.config = config;

            if (initial || myq.failed) {
                myq.start(app, config, function (event) {
                    doProcessEvent(event);
                });
            }
        } catch (e) {
            console.error('Error starting myq: ' + e);
        }
    };

    /**
     * Refresh security tokens
     */
    this.refreshTokens = function () {

        try {
            if (config.server && config.server.ip && config.server.port) {
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
        }
    };
};

// initialize the app
app.init();
