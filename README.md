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
 1. Check the application, check *Publish*, and click *Execute Update*
 
#### 2. Install Device Handlers
 
 1. Go to *My Device Handlers*
 1. Click on *Update from Repo* button and select `MyQController (master)`
 1. Select devices that you want to install (Garage Door, Switch)
 1. check *Publish*, and click *Execute Update*

#### 3. Installing Local Server

Prerequisites: You must have node and npm installed on your system.

 1. Run `git clone https://github.com/aromka/myqcontroller.git` from directory where you want the server installed
 1. Run `npm install`
 1. Find out the IP and Port of your SmartThings hub 
    - either from your router, 
    - or go to *My Hubs* in SmartThings IDE and look for `localIP` and `localSrvPortTCP`
 1. Copy `server/config/config.json.example` to `server/config/config.json` 
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
 1. You should also see all the devices that were found, as well as any commands sent in your console running the server
    
    
# Raspberry Pi setup

Installing on a fresh copy of [Raspbian NOOB](https://www.raspberrypi.org/downloads/noobs/) on [Raspberry Pi 3](https://www.amazon.com/gp/product/B01CD5VC92/ref=as_li_tl?ie=UTF8&camp=1789&creative=9325&creativeASIN=B01CD5VC92&linkCode=as2&tag=aromka-20&linkId=ae74c6aa2ea4a794b8662d6c9dcdc464)

You can either do this through Raspberry Pi's console, or ssh to it from your mac / pc.

If you want to ssh and run the commands from your mac, the default "Raspbian" OS will automatically broadcast its presence on your network under the mDNS name `raspberrypi`. If you are using Mac or Linux, you can reach your Pi easily:

    ssh pi@raspberrypi.local
    
The default username for Raspbian is `pi` and the password is `raspberry`.

Once you login, update the system and install npm:
 
    sudo apt-get update
    sudo apt-get upgrade
    sudo apt-get npm
   
Optionally create a folder where you want to store MyQController app
 
    mkdir ~/Apps
    cd ~/Apps
    git clone https://github.com/aromka/myqcontroller.git
    cd myqcontroller && npm install
    
Update config.json file

    cd ~/Apps/myqcontroller/server/config
    cp config.json.example config.json
    nano config.json

And set your SmartThings Hub's IP and port. Now run the server.
Find out your Raspberry Pi's IP address on your local network (you will need to set it in the app)

    hostname -I
    
Run the server
    
    node server
    
And update the IP in MyQ Controller SmartApp in the SmartThings app.


#### Running the server on the background / after bootup

You can add a command to your /etc/rc.local

    sudo nano /etc/rc.local
    
and add the following content right before `exit 0`

    exec 2> /tmp/rc.local.log      # send stderr from rc.local to a log file
    exec 1>&2                      # send stdout to the same log file
    set -x                         # tell sh to display commands before execution
    
    node /home/pi/Apps/myqcontroller/server &

This will run the MyQController server after raspberry pi boots up, and will log the output to `/tmp/rc.local.log` file.

Restart the system

    sudo reboot
    
You can tail the logs to make sure everything works as expected

    tail -f /tmp/rc.local.log 
    

    
# Known issues
 
 * When you PC / Mac restarts when running a node server, you might get a different IP address, so app settings need to be update to assign the new IP.
 
 * If you run a server first time, and shortly kill it, and run it again - duplicate devices might be created, as it seems like ST doesn't return newly created devices within first few minutes. You can simply go and delete those duplicate devices to solve this.
