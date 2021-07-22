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
10.10.10	1.1.01	Updates to match other platform.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.1.01" }

metadata {
	definition (name: "bleBox wLightBoxS",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.light") {
		capability "Light"
		capability "Switch"
		capability "Switch Level"
		capability "Actuator"
        capability "Health Check"
        capability "Refresh"
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
        valueTile("blankTile", "", width: 4, height: 2)
		valueTile("refresh", "capability.refresh", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'Refresh', action: 'refresh'
		}
		main("switch")
		details("switch", "refresh")
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
	state.savedLevel = "FF"
	updated()
}

def updated() {
	state.notice = "<b>Developmental Version.  This is the pre-Alpha version of this driver.</b>"
	logInfo("Updating...")
	unschedule()

	if (!getDataValue("applicationVersion")) {
		if (!device_IP) {
			logWarn("updated:  deviceIP  is not set.")
			return
		}
		updateDataValue("deviceIP", device_IP)
		logInfo("Device IP set to ${getDataValue("deviceIP")}")
		//	Update device name on manual installation to standard name
		sendGetCmd("/api/device/state", "setDeviceData")
	}

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}

	if (!transTime) { state.defFadeSpeed = getFadeSpeed(1) }
    else { state.defFadeSpeed = getFadeSpeed(transTime) }
	updateDataValue("driverVersion", driverVer())

	refresh()
}

def setDeviceName(response) {
	def cmdResponse = parseInput(response)
	logDebug("setDeviceData: ${cmdResponse}")
	device.setName(cmdResponse.device.type)
	logInfo("setDeviceData: Device Name updated to ${cmdResponse.device.type}")
}


//	===== Commands and Parse Returns =====
def on() {
	logDebug("on")
	sendPostCmd("/api/light/set",
				"""{"light":{"desiredColor":"${state.savedLevel}","fadeSpeed":${state.defFadeSpeed}}}""",
				"commandParse")
}

def off() {
	logDebug("off")
	sendPostCmd("/api/light/set",
				"""{"light":{"desiredColor":"00","fadeSpeed":${state.defFadeSpeed}}}""",
				"commandParse")
}

def setLevel(level, transitionTime = null) {
	def fadeSpeed = state.defFadeSpeed
	if (transitionTime != null) { fadeSpeed = getFadeSpeed(transitionTime) }
	logDebug("setLevel: level = ${level} // ${fadeSpeed}")
	level = (2.55 * level + 0.5).toInteger()
	def hexLevel = Integer.toHexString(level).padLeft(2,'0')
	state.savedLevel = "${hexLevel}"
	sendPostCmd("/api/light/set",
				"""{"light":{"desiredColor":"${hexLevel}","fadeSpeed":${fadeSpeed}}}""",
				"commandParse")
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

def ping() { refresh() }

def refresh() {
	logDebug("refresh")
	sendGetCmd("/api/light/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: response = ${cmdResponse}")
	def hexLevel = cmdResponse.light.desiredColor.toUpperCase()
	if (hexLevel == "00") {
		sendEvent(name: "switch", value: "off")
		sendEvent(name: "level", value: 0)
		logInfo "commandParse: switch = off"
	} else {
		sendEvent(name: "switch", value: "on")
		def brightness = Integer.parseInt(hexLevel,16)
		def level = (0.5 + brightness/ 2.55).toInteger()
		sendEvent(name: "level", value: level)
		logInfo "commandParse: switch = on, level = ${level}"
	}
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
	logDebug("sendPostCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "POST",
					  path: command,
					  body: body,
					  headers: [
						  Host: "${getDataValue("deviceIP")}:80"
					  ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}
def parseInput(response) {
	try {
		def jsonSlurper = new groovy.json.JsonSlurper()
		return jsonSlurper.parseText(response.body)
	} catch (error) {
		logWarn "CommsError: ${error}."
	}
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