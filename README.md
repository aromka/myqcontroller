# MyQController

SmartThings integration for MyQ Garage Doors and Switches
Based on [ady642/HomeCloudHub](https://github.com/ady624/HomeCloudHub)

# Installation

#### 1. Install the SmartApp

 1. Go to your [SmartThings IDE](https://graph.api.smartthings.com/login/auth)
 1. Go to *My SmartApps* link 
 1. Click on *Settings* button 
 1. Click *Add new repository*
 1. Enter owner `aromka`, name `MyQController`, branch `master`. 
 1. Click *Save*
 1. Click on the *Update from Repo* button 
 1. Select the `MyQController (master)` repository
 1. Select the `aromka:MyQ Controller` application
 1. Check *Publish* and click *Execute Update*
 
#### 2. Install Device Handlers
 
 1. Go to [My Device Handlers](https://graph.api.smartthings.com/ide/devices)
 1. Add repository, same as for SmartApp
 1. Click on *Update from Repo* button and select `MyQController (master)`
 1. Select devices that you want to install (Garage Door, Switch)
 1. Check *Publish* and click *Execute Update*

#### 3. Installing Local Server

Prerequisites: You must have node and npm installed on your system.

 1. Run `npm install myqcontroller` from directory where you want the server installed
 1. Find out the IP and Port of your SmartThings hub 
    - either from your router, 
    - or go to *My Hubs* in SmartThings IDE and look for `localIP` and `localSrvPortTCP`
 1. Open `server/config/config.json` file and set `ip` and `port` variables
 1. Save and close config file

#### 4. Running and Using SmartThings App

 1. Run the server `node server` from `myqcontroller` directory
 1. Open SmartThings app
 1. Go to Marketplace -> SmartApps tab
 1. Scroll down and go to *MyApps*
 1. Select *MyQ Controller*
 1. Enter the IP of your local server 
    (This should be pc/mac that's running node server. You can find this out by going to Network Preferences, usually it's something like `192.168.0.5`. If you have firewall enabled, make sure to open port `42457`)
 1. Enter your MyQ username and password 
    (Your credentials are stored in your SmartThings account, and never used or shared outside of this SmartApp)
 1. Press *Next*
 1. If you entered everything correctly, you should see success confirmation message
 1. Press *Done*
 1. Your devices should appear in *My Home* -> *Things*
    
    
# Known issues
 
 * When you PC / Mac restarts when running a node server, you might get a different IP address, so app settings need to be update to assign the new IP.
 
 * If you run a server first time, and shortly kill it, and run it again - duplicate devices might be created, as it seems like ST doesn't return newly created devices within first few minutes. You can simply go and delete those duplicate devices to solve this.
