metadata {
    definition (name: "ZOOZ ZSE40 4-in-1 sensor", namespace: "skyjunky", author: "Simon Capper") {
        capability "Battery"
        capability "Motion Sensor"
        capability "Tamper Alert"
        capability "Temperature Measurement"
        capability "Configuration"
        capability "Relative Humidity Measurement"
        capability "Illuminance Measurement"
        
        attribute "sensorlevels", "string"
        attribute "primaryDisplay", "string"
        
        fingerprint deviceId: "0x0701", inClusters: "0x5E,0x98,0x72,0x5A,0x85,0x59,0x73,0x80,0x71,0x31,0x70,0x84,0x7A"
        fingerprint deviceId: "0x0701", inClusters: "0x5E,0x98,0x86,0x72,0x5A,0x85,0x59,0x73,0x80,0x71,0x31,0x70,0x84,0x7A"
    }

    tiles(scale:2) {
        multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
            tileAttribute ("device.primaryDisplay", key: "PRIMARY_CONTROL") {
                attributeState "",  label: '${currentValue}', backgroundColors:[
                   [value: 31, color: "#153591"],
                   [value: 44, color: "#1e9cbb"],
                   [value: 59, color: "#90d2a7"],
                   [value: 74, color: "#44b621"],
                   [value: 84, color: "#f1d801"],
                   [value: 95, color: "#d04e00"],
                   [value: 96, color: "#bc2323"]
                ]
                attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
                attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
            }
            tileAttribute ("device.sensorlevels", key: "SECONDARY_CONTROL") {
                attributeState "sensorlevels", label: '${currentValue}'
            }
        }
        standardTile("tamper", "device.tamper", decoration: "flat", width: 2, height: 2)
        {
            state("clear", label:'Secure', defaultState: true, icon:"", backgroundColor: "#ffffff"  )
            state("detected", label:'Tamper', icon:"st.alarm.alarm.alarm", backgroundColor: "#ffffff" )
        }
        valueTile("battery", "device.battery", decoration: "flat", width: 2, height: 2) 
        {
            state("battery", label:'${currentValue}% battery')
        }
        main "motion"
        details(["motion", "tamper", "battery"])
    }
    
    preferences {
        input "primaryDisplayType", "enum", options: ["Motion", "Temperature"], title: "Primary Display", defaultValue: "Motion",
              description: "Sensor to show in primary display",
              required: false, displayDuringSetup: true
        input "pirTimeout", "number", title: "Motion Sensor Idle Time (minutes)", defaultValue: 3,
              description: "Inactivity time before reporting no motion",
              required: false, displayDuringSetup: true, range: "1..255"
        input "pirSensitivity", "number", title: "Motion Sensor Sensitivity (1 high - 7 low)", defaultValue: 3,
              description: "1 is most sensitive, 7 is least sensitive",
              required: false, displayDuringSetup: true, range: "1..7"
        input "tempAlert", "number", title: "Temperature reporting level (1/10th °C)", defaultValue: 10,
              description: "Minimum temperature change to trigger temp updates",
              required: false, displayDuringSetup: true, range: "1..50"
        input "humidityAlert", "number", title: "Humidity reporting level", defaultValue: 50,
              description: "Minimum humidity level change to trigger updates",
              required: false, displayDuringSetup: true, range: "1..50"
        input "illumSensorAlerts", "enum", options: ["Enabled", "Disabled"], title: "Enable Illumination Sensor Updates", defaultValue: "Disabled",
              description: "Enables illumination update events",
              required: false, displayDuringSetup: true
        input "illumAlert", "number", title: "Illumination reporting level", defaultValue: 50,
              description: "Minumum illumination level change to trigger updates",
              required: false, displayDuringSetup: true, range: "5..50"
        input "ledMode", "number", title: "LED Mode", defaultValue: 3,
              description: "1 LED Off, 2 - Always on (drains battery), 3 - Blink LED",
              required: false, displayDuringSetup: true, range: "1..3"
        input "wakeInterval", "number", title: "Wake Interval (minutes)", defaultValue: 60,
              description: "Interval (in minutes) for polling configuration and sensor values, shorter interval reduces battery life.",
              required: false, displayDuringSetup: true, range: "10..10080"
    }
}

// parse events into attributes
def parse(String description) {
    def result = null
    // supported classes
    // 0x20 - BasicSet, reports when motion detected (alarm provides same info)
    // 0x70 - configuration V1
    // 0x72 - manufacturer specific V2
    // 0x80 - Battery V1
    // 0x84 - Wakeup V2
    // 0x85 - association V2
    // 0x86 - version V2
    // 0x5E - zwave plus info V2 (not supported by ST)
    // 0x98 - Security V1
    // 0x5A - Device Reset Locally V1
    // 0x59 - Association Grp Info V1
    // 0x73 - Powerlevel (RF power) V1
    // 0x71 - Notification V4
    // 0x31 - Sensor Multilevel V7
    // 0x7A - Firmware Update Md V2
    log.debug "raw: ${description}"
    state.sec = 0
    if (description.contains("command: 5E02")) {
        // for some reason this causes a crash in parse(), so skip it
        return null
    }
    def cmd = zwave.parse(description, 
                        [ 0x86:1, 0x70:1, 0x72:1, 0x80:1, 0x84:2, 0x85:1, 0x86:2, 
                          0x98:1, 0x5a:1, 0x59:1, 0x73:1, 0x71:3, 0x31:5, 0x7a:1 ])
    if (cmd) {
        result = zwaveEvent(cmd)
        if (result) {
            log.debug "Parsed command: ${result} raw: ${description}"
        } else {
            log.debug "Unhandled command: ${description}"
        }
    } else {
        log.debug "Unparsed command: ${description}"
    }
    return result
}

def updateDisplay(String source) {
    def evt = []
    switch (primaryDisplayType) {
       default: // motion is the default
           if(source == "motion") {
               evt += createEvent([name:"primaryDisplay", value: "${state.motion}", displayed:false])
           } else {
               evt += createEvent([name: "sensorlevels", value: "Temp:${state.temp}°F   Humidity:${state.humidity}%   Light:${state.illuminance}"])
           }
           break
       case "Temperature": 
           if(source == "multi") {
               evt += createEvent([name:"primaryDisplay", value: state.temp, displayed:false])
           }
           evt += createEvent([name: "sensorlevels", value: "${state.motionText}   Humidity:${state.humidity}%   Light:${state.illuminance}"])
           break
    }
    return evt
}

def handleMotion(boolean motion) {
	def evt = []

	if(motion && state.motion != "active") {
        state.motion = "active"
        state.motionText = "Motion"
        evt += updateDisplay("motion")
        evt += createEvent([name:"motion", value: "active"])
    } else if (!motion && state.motion != "inactive") {
        state.motion = "inactive"
        state.motionText = "No Motion"
        evt += updateDisplay("motion")
        evt += createEvent([name:"motion", value: "inactive"])
    }
    return evt
}	

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	log.debug "BasicSet ${cmd}"
    // Zooz usually reports a burglar and a basic set event when motion is detected/cleared
    // sometimes the burglar event is missing so we use on the 
    // BasicSet to detect motion
    def evt = []
    if(cmd.value) { // motion detected
    	evt += handleMotion(true)
    } else { // no motion 
    	evt += handleMotion(false)
    }
    return evt
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
    log.debug "NotificationReport ${cmd}"
    def evt = []
    // zooz reports 2 "burgler" event types, motion (8) and tamper (3)
    if (cmd.notificationType == 7) {
        switch (cmd.event) {
            case 0:
                if (cmd.eventParameter == [8]) {
                	// probably obsolete as BasicSet reports motion
                    evt += handleMotion(false)
                    return evt
                } else if (cmd.eventParameter == [3]) {
                    // clear the batterytime to force a battery read
                    state.batteryReadTime = 0
                    // force a parameter update, if the batteries are changed
                    // some parameters get set back to default
                    state.configRequired = true
                    return createEvent(name:"tamper", value: "clear")
                }
                break
            case 3:
                return createEvent(name:"tamper", value: "detected")
                break
            case 8:
                // probably obsolete as BasicSet reports motion
                evt += handleMotion(true)
                return evt
                break
        }
    }
    return null
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
    log.debug "SensorMultilevelReport ${cmd}"
    def evt = []
    switch (cmd.sensorType) {
        case 1: //temp
            if (cmd.scale == 0) {
                // Celcius, convert to Fahrenheit
                def temp = ((cmd.scaledSensorValue * 9) / 5) + 32
                state.temp = Math.round(temp * 100)/100
            } else {
                state.temp = cmd.scaledSensorValue
            }
            evt += createEvent([name: "temperature", value: state.temp, unit: "fahrenheit"]) 
            break
        case 3: // light
            state.illuminance = cmd.scaledSensorValue 
            evt += createEvent([name: "illuminance", value: state.illuminance]) 
            break
        case 5: // humidity
            state.humidity = cmd.scaledSensorValue
            evt += createEvent([name: "humidity", value: state.humidity]) 
            break
    }
    evt += updateDisplay("multi")
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def secCmd = cmd.encapsulatedCommand([ 0x86:1, 0x70:1, 0x72:1, 0x80:1, 0x84:2, 0x85:1, 0x86:1,
                                           0x98:1, 0x5a:1, 0x59:1, 0x73:1, 0x71:3, 0x31:5, 0x7a:1 ])
    if (secCmd) {
        state.sec = 1
        return zwaveEvent(secCmd)
    } else {
        log.debug "SecurityMessageEncapsulation cannot decode ${cmd}"
    }
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
   def level = cmd.batteryLevel
    def desc = ""
    if (level > 100) {
        level = 1
        desc = "${device.displayName}, Battery level low"
    }
    state.batteryReadTime = new Date().time    
    return createEvent(name:"battery", value: level, description: desc)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
    log.debug "WakeUpNotification v2 ${cmd}"

    def cmds = readSensors()
    // read the battery level every day
    log.debug "battery ${state.batteryReadTime} ${new Date().time}"
    if (!state.batteryReadTime || (new Date().time) - state.batteryReadTime > 24*60*60*1000) {
        cmds += secure(zwave.batteryV1.batteryGet())
    }
    if (state.configRequired) {
        // send pending configure commands
        cmds += configCmds()
        state.configRequired = false
    }
    cmds += zwave.wakeUpV2.wakeUpNoMoreInformation().format()
    cmds = delayBetween(cmds, 600)
    return [response(cmds)]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Unhandled ${cmd}"
    // Handles all Z-Wave commands we aren't interested in
    return null
}

def updated() {
    def evt = []
    log.debug "updated"
    // called when the device is updated or perferences changed
    state.configRequired = true
    state.batteryReadTime = 0
    if(!state.primaryDisplayType) state.primaryDisplayType = "Motion"
    if(!state.illumSensorAlerts) state.illumSensorAlerts = "Disabled"
    // set default values so the display isn't messed up
    if(state.motion == null || state.motionText == null) {
        state.motion = "inactive"
        state.motionText = "No Motion"
    }
    evt += updateDisplay("multi")
    evt += updateDisplay("motion")
    for(Map e : evt ) {
        sendEvent(e)
    }
}

def installed() {
   log.debug "installed"
    // called when the device is installed
    state.configRequired = true
    state.batteryReadTime = 0
    if(!state.primaryDisplayType) state.primaryDisplayType = "Motion"
    if(!state.illumSensorAlerts) state.illumSensorAlerts = "Disabled"
}

def configure() {
    state.sec = 1
    delayBetween( configCmds() + readSensors(), 600)
}

def readSensors() {
    [
        secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:3)), // light
        secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:5)), // humidity
        secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1)), // temp
    ]
}

def getWakeIntervalPref() {
    (wakeInterval < 10 || wakeInterval > 10080) ? 3600 : (wakeInterval * 60).toInteger()
}

def getLedModePref() {
    (ledMode < 1 || ledMode > 3) ? 3 : ledMode.toInteger()
}

def getIllumAlertPref() {
    if(illumSensorAlerts == "Disabled") {
        return 0
    } else { // alerts enabled
        return (illumAlert >= 5 && illumAlert <= 50) ? illumAlert.toInteger() : 50
    }
}

def getHumidityAlertPref() {
    (humidityAlert < 1 || humidityAlert > 50) ? 50 : humidityAlert.toInteger()
}

def getTempAlertPref() {
    (tempAlert < 1 || tempAlert > 50 ) ? 10 : tempAlert.toInteger()
}

def getPirTimeoutPref() {
    (pirTimeout < 1 || pirTimeout > 255 ) ? 3 : pirTimeout.toInteger()
}

def getPirSensitivityPref() {
    (pirSensitivity < 1 || pirSensitivity > 7) ? 3 : pirSensitivity.toInteger()
}

def configCmds() {
    log.debug "configure, tempAlertPref:${tempAlertPref} humidityAlertPref:${humidityAlertPref}"
    log.debug "configure, illumAlertPref:${illumAlertPref} pirTimeoutPref:${pirTimeoutPref}"
    log.debug "configure, pirSensitivityPref:${pirSensitivityPref} ledModePref:${ledModePref}" 
    log.debug "configure, wakeIntervalPref:${wakeIntervalPref}"
    def cmds = [
        secure(zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)),

        secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: tempAlertPref, parameterNumber: 2)),
        secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: humidityAlertPref, parameterNumber: 3)),
        secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: illumAlertPref, parameterNumber: 4)),
        secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: pirTimeoutPref, parameterNumber: 5)),
        secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: pirSensitivityPref, parameterNumber: 6)),
        secure(zwave.configurationV1.configurationSet(scaledConfigurationValue: ledModePref, parameterNumber: 7)),

        secure(zwave.wakeUpV2.wakeUpIntervalSet(seconds: wakeIntervalPref, nodeid:zwaveHubNodeId)),
        


/*
        secure(zwave.configurationV1.configurationGet(parameterNumber: 1)), // Temp scale C(0) or F(1)
        secure(zwave.configurationV1.configurationGet(parameterNumber: 2)), // Temp trigger 0.1 - 5 (1-50)
        secure(zwave.configurationV1.configurationGet(parameterNumber: 4)), // Light trigger, None(0), 5%-50%
        secure(zwave.configurationV1.configurationGet(parameterNumber: 5)), // Motion Timeout 1-255 Mins
        secure(zwave.configurationV1.configurationGet(parameterNumber: 6)), // Motion Sensitivity 1-7 (1 is most sensitive)
        secure(zwave.configurationV1.configurationGet(parameterNumber: 7)), // LED Mode (1,2 or 3). 
                                                                            // - 1 is Off
                                                                            // - 2 is fancy temp color scheme that wastes battery 
                                                                            // - 3 is brief flash when temp/motion trigger fires
*/
        
    ]
    return cmds
}

def secure(cmd) {
    if (state.sec) {
        return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        return cmd.format()
    }
}