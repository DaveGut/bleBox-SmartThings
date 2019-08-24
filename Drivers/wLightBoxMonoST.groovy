/*
===== Blebox Hubitat Integration Driver

	Copyright 2019, Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into SmartThings.

===== Hiatory =====
08.22.19	1.0.01	Initial release.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.0.01" }
metadata {
	definition (name: "bleBox wLightBox Mono",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.light") {
		capability "Light"
		capability "Switch"
		capability "Actuator"
		capability "Switch Level"
        capability "Health Check"
        capability "Refresh"
		command "level1Set", ["NUMBER"]
		attribute "ch1Level", "number"
		command "level2Set", ["NUMBER"]
		attribute "ch2Level", "number"
		command "level3Set", ["NUMBER"]
		attribute "ch3Level", "number"
		command "level4Set", ["NUMBER"]
		attribute "ch4Level", "number"
	}
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action: "switch.off", 
                	icon: "st.Lighting.light13", backgroundColor: "#00a0dc", nextState: "off"
				attributeState "off", label:'${name}', action: "switch.on",
                	icon: "st.Lighting.light13", backgroundColor: "#ffffff", nextState: "on"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", label: "Brightness: ${currentValue}", 
                	action: "switch level.setLevel"
			}
		}
        valueTile("label1", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 1) {
			state "default", label: 'Level 1'
		}
		controlTile("level1Set", "device.ch1Level", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..100)") { state "ch1Level", action: "level1Set" }
        valueTile("label2", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 1) {
			state "default", label: 'Level 2'
		}
		controlTile("level2Set", "device.ch2Level", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..100)") { state "ch2Level", action: "level2Set" }
        valueTile("label3", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 1) {
			state "default", label: 'Level 3'
		}
		controlTile("level3Set", "device.ch3Level", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..100)") { state "ch3Level", action: "level3Set" }
        valueTile("label4", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 1) {
			state "default", label: 'Level 4'
		}
		controlTile("level4Set", "device.ch4Level", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..100)") { state "ch4Level", action: "level4Set" }
		valueTile("refresh", "capability.refresh", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'Refresh', action: 'refresh'
		}
		main("switch")
		details(["switch", "label1", "level1Set", "label2", "level2Set", 
        		"label3", "level3Set", "label4", "level4Set", "Refresh"])
	}
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("transTime", "number", title: "Default Transition time (0 - 60 seconds maximum)")
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}
def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
	state.savedLevel = "00000000"
	state.savedCh1 = "00"
	state.savedCh2 = "00"
	state.savedCh3 = "00"
	state.savedCh4 = "00"
	runIn(2, updated)
}
def updated() {
	logInfo("Updating...")
	unschedule()

	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated:  deviceIP  is not set.")
			return
		}
		updateDataValue("deviceIP", device_IP)
		logInfo("Device IP set to ${getDataValue("deviceIP")}")
	}

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}

	if (!transTime) { state.defFadeSpeed = getFadeSpeed(1) }
    else { state.defFadeSpeed = getFadeSpeed(transTime) }

	refresh()
}


//	===== Commands and Parse Returns =====
def on() {
	logDebug("on: ${state.savedLevel} / ${state.defFadeSpeed}")
	sendPost(state.savedLevel, state.defFadeSpeed)
}
def off() {
	logDebug("off: ${state.defFadeSpeed}")
	sendPost("00000000", state.defFadeSpeed)
}
def setLevel(level, transTime = null) {
	//	Sets all four channels to the same level.
	logDebug("setLevel: level = ${level})")
	level = (level * 2.55).toInteger()
log.error level
	level = Integer.toHexString(level).padLeft(2,'0')
log.error level
	sendPost("${level}${level}${level}${level}", state.defFadeSpeed)
}
def level1Set(ch1Level) {
	logDebug("setChannel1: ${ch1Level})")
	if (!ch1Level) { return }
	ch1Level = (ch1Level * 2.55).toInteger()
	ch1Level = Integer.toHexString(ch1Level).padLeft(2,'0')
	def chData = ch1Level + state.savedCh2 + state.savedCh3 + state.savedCh4
	sendPost(chData, state.defFadeSpeed)
}
def level2Set(ch2Level) {
	logDebug("setChannel2: ${ch2Level})")
	if (!ch2Level) { return }
	ch2Level = (ch2Level * 2.55).toInteger()
	ch2Level = Integer.toHexString(ch2Level).padLeft(2,'0')
	def chData = state.savedCh1 + ch2Level + state.savedCh3 + state.savedCh4
	sendPost(chData, state.defFadeSpeed)
}
def level3Set(ch3Level) {
	logDebug("setChannel3: ${ch3Level})")
	if (!ch3Level) { return }
	ch3Level = (ch3Level * 2.55).toInteger()
	ch3Level = Integer.toHexString(ch3Level).padLeft(2,'0')
	def chData = state.savedCh1 + state.savedCh2 + ch3Level + state.savedCh4
	sendPost(chData, state.defFadeSpeed)
}
def level4Set(ch4Level) {
	logDebug("setChannel4: ${ch4Level})")
	if (!ch4Level) { return }
	ch4Level = (ch4Level * 2.55).toInteger()
	ch4Level = Integer.toHexString(ch4Level).padLeft(2,'0')
	def chData = state.savedCh1 + state.savedCh2 + state.savedCh3 + ch4Level
	sendPost(chData, state.defFadeSpeed)
}
def getFadeSpeed(transitionTime) {
	logDebug("getFadeSpeed: ${transitionTime}")
	def timeIndex = (10 * transitionTime).toInteger()
	def fadeSpeed
	switch (timeIndex) {
		case 0: fadeSpeed = 255; break
		case 1..7 :		fadeSpeed = 234; break
		case 8..15 :	fadeSpeed = 229; break
		case 16..25 :	fadeSpeed = 219; break
		case 26..35 : 	fadeSpeed = 215; break
		case 36..45 : 	fadeSpeed = 213; break
		case 46..55 : 	fadeSpeed = 212; break
		case 56..65 :	fadeSpeed = 211; break
		case 66..90 : 	fadeSpeed = 209; break
		case 91..125 : 	fadeSpeed = 207; break
		case 126..175 : fadeSpeed = 202; break
		case 176..225 : fadeSpeed = 199; break
		case 226..275 : fadeSpeed = 197; break
		case 276..350 :	fadeSpeed = 194; break
		case 351..450 : fadeSpeed = 189; break
		case 451..550 : fadeSpeed = 185; break
		default: fadeSpeed = 179
	}
	return fadeSpeed
}
def sendPost(rgbw, fadeSpeed) {
	sendPostCmd("/api/rgbw/set",
				"""{"rgbw":{"desiredColor":"${rgbw}","fadeSpeed":${fadeSpeed}}}""",
				"commandParse")
}	
def ping() {
	logDebug("ping")
    refresh()
}
def refresh() {
	logDebug("refresh.")
	sendGetCmd("/api/rgbw/state", "commandParse")
}
def commandParse(response) {
	if(response.status != 200 || response.body == null) {
		logWarn("parseInput: Command generated an error return: ${response.status} / ${response.body}")
		return
	}
	def jsonSlurper = new groovy.json.JsonSlurper()
	def cmdResponse = jsonSlurper.parseText(response.body)
	logInfo("commandParse: cmdResponse = ${cmdResponse}")
	def hexDesired = cmdResponse.rgbw.desiredColor.toUpperCase()
	if (hexDesired == "00000000") {
		sendEvent(name: "switch", value: "off")
		sendEvent(name: "ch1Level", value: 0)
		sendEvent(name: "ch2Level", value: 0)
		sendEvent(name: "ch3Level", value: 0)
		sendEvent(name: "ch4Level", value: 0)
		sendEvent(name: "level", value: 0)
	} else {
		sendEvent(name: "switch", value: "on")
		state.savedLevel = hexDesired
	}

	state.savedCh1 = hexDesired[0..1]
	def ch1Level = Integer.parseInt(hexDesired[0..1], 16)
	ch1Level = (0.5 + (ch1Level / 2.55)).toInteger()
	sendEvent(name: "ch1Level", value: ch1Level)

	state.savedCh2 = hexDesired[2..3]
	def ch2Level = Integer.parseInt(hexDesired[2..3], 16)
	ch2Level = (0.5 + (ch2Level / 2.55)).toInteger()
	sendEvent(name: "ch2Level", value: ch2Level)

	state.savedCh3 = hexDesired[4..5]
	def ch3Level = Integer.parseInt(hexDesired[4..5], 16)
	ch3Level = (0.5 + (ch3Level / 2.55)).toInteger()
	sendEvent(name: "ch3Level", value: ch3Level)

	state.savedCh4 = hexDesired[6..7]
	def ch4Level = Integer.parseInt(hexDesired[6..7], 16)
	ch4Level = (0.5 + (ch4Level / 2.55)).toInteger()
	sendEvent(name: "ch4Level", value: ch4Level)

	def level = [ch1Level, ch2Level, ch3Level, ch4Level].max()
	sendEvent(name: "level", value: level)
}


//	===== Communications =====
private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "GET",
    				  path: command,
                      headers: [
                          Host: "${getDataValue("deviceIP")}:80"
                      ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}
private sendPostCmd(command, body, action){
	logDebug("sendPostCmd: ${command} // ${getDataValue("deviceIP")} // ${body }// ${action}")
	def parameters = [method: "POST",
					  path: command,
					  body: body,
					  headers: [
						  Host: "${getDataValue("deviceIP")}:80"
					  ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}


//	===== Utility Methods =====
def logInfo(msg) {
	if (descriptionText != false) { log.info "${device.label} ${driverVer()}:   ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()}:   ${msg}" }
}
def logWarn(msg){ log.warn "${device.label} ${driverVer()}:    ${msg}" }

//	end-of-file