/*
===== Blebox SmartThings Integration Driver

	Copyright 2019, Dave Gutheinz

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into the SmartThings Environment.

===== Hiatory =====
07.31.21	Special combined RBWB single driver version.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.3.01" }

metadata {
	definition (name: "bleBox wLightBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.switch", 
                mnmn: "SmartThings", 
                vid: "generic-dimmer") {
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
				attributeState "level", label: "Brightness: ${currentValue}", action: "setLevel"
			}
		}
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
        valueTile("blankTile", "", width: 4, height: 2)
        main(["switch"])
        details(["switch", "blankTile", "refresh"])
	}
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("fadeSpeed", "number", title: "Default Transition time (0 - 60 seconds)")
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}

def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
	state.savedRgbw = "00000000"
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

	if(!getDataValue("driverVersion")) {
		updateDataValue("driverVersion", driverVer())
		sendGetCmd("/api/device/state", "setDeviceName")
	}
	
	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}
	logInfo("Refresh interval set for every ${refreshInterval} minute(s).")

	def defFadeSpeed = 1
    if (!fadeSpeed) { fadeSpeed = defFadeSpeed }
    state.transTime = defFadeSpeed
    logInfo("state.transTime set to ${defFadeSpeed}")
    if (!debug) { debug = true }
	logInfo("Debug logging is: ${debug}.")
    if (!descriptionText) { descriptionText = true }
	logInfo("Description text logging is ${descriptionText}.")
    
	runIn(2, refresh)
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
	setRgbw(state.savedLevel)
}

def off() {
	logDebug("off")
	setRgbw("00000000")
}

def setLevel(level, fadeSpeed = state.transTime) {
	logDebug("setLevel: level = ${level}), hue = ${state.hue}, saturation = ${state.saturation}")
    def transTime = 1000* fadeSpeed
    if (level == 0) {
    	setRgbw("00000000", transTime)
    } else {
    	def hue = (0.5 + state.hue).toInteger()
        def saturation = (0.5 + state.saturation).toInteger()
        def rgb = colorUtil.hsvToHex(hue, saturation, level).substring(1,7).toLowerCase()
        def rgbw = rgb + state.savedRgbw[6..7]
    	setRgbw(rgbw, transTime)
    }
    logInfo("setLevel: level set to ${level}")
}

def setRgbw(rgbw, transTime = state.transTime) {
	//	Common method to send new rgbw to device.
	logDebug("setRgbw: ${rgbw} / ${transTime}")
	def fadeSpeed = 1000 * transTime.toInteger()
	sendPostCmd("/api/rgbw/set",
				"""{"rgbw":{"desiredColor":"${rgbw}","durationsMs":{"colorFade":${fadeSpeed}}}}""",
				"commandParse")
}

def ping() { refresh() }

def refresh() {
	logDebug("refresh.")
	sendGetCmd("/api/rgbw/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: ${cmdResponse}")
	def hexDesired = cmdResponse.rgbw.desiredColor.toUpperCase()
	if (hexDesired == "00000000") {
		sendEvent(name: "switch", value: "off")
		logInfo("Device is off.")
	} else {
		sendEvent(name: "switch", value: "on")
		state.savedRgbw = hexDesired
	    def hsv = colorUtil.hexToHsv(hexDesired[0..5])
        state.hue = hsv[0]
        state.saturation = hsv[1]
        def level = hsv[2].toInteger()
		sendEvent(name: "level", value: level)
		def color = ["hue": hsv[0], "saturation": hsv[1], "level": hsv[2]]
		sendEvent(name: "color", value: color)
        logInfo("parseReturnData: Device is on. Color: ${color}, white: ${whiteLevel}, level: ${level}")
	}
}

//	===== Communications =====
private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} / ${action} / ${getDataValue("deviceIP")}")
	def parameters = [method: "GET",
    				  path: command,
                      headers: [
                          Host: "${getDataValue("deviceIP")}:80"
                      ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}

private sendPostCmd(command, body, action){
	logDebug("sendGetCmd: ${command} / ${body} / ${action} / ${getDataValue("deviceIP")}")
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