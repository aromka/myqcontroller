# MyQ Controller

Slim version of HomeCloudHub by ady642 (https://github.com/ady624/HomeCloudHub)
Only includes MyQ - garage door opener + switch control

# Installation

**Note:** There are two parts to the installation:

 * Install the SmartApp and its associated Device Handlers
 * Install the MyQ Controller NodeJS server

# Installing the SmartApp and its associated Device Handlers

Go to your SmartThings [IDE](https://graph.api.smartthings.com/login/auth) and go to your [SmartApps](https://graph.api.smartthings.com/ide/apps). Click on Settings and add a new repository with owner **aromka**, name **MyQLocalHub** and branch **master**. Click Ok.

Click on the Update from Repo button and select the MyQLocalHub repo. Select the MyQLocalHub application and install it. Do the same for the Device Handlers, selecting whichever devices you plan on using.

# Installing the MyQLocalHub NodeJS server

    git clone the repository 
    run npm install
    set the IP and PORT of your SmartThings hub in server/config/config.json
    

Test the application:

    node server
