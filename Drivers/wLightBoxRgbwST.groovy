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
	definition (name: "bleBox wLightBox RGBW",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.light") {
		capability "Light"
		capability "Switch"
		capability "Actuator"
		capability "Switch Level"
		capability "Color Control"
		capability "Color Mode"
		capability "Health Check"
        capability "Refresh"
		command "setWhiteLevel", ["NUMBER"]
		attribute "whiteLevel", "number"
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
			tileAttribute ("device.color", key: "COLOR_CONTROL") {
				attributeState "color", action: "setColor"
			}
		}
		valueTile("whiteLevelLabel", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'White Level'
		}
		controlTile("setWhiteLevel", "device.whiteLevel", "slider", width: 4, height: 1,
        	inactiveLabel: false, range: "(0..100)") {
			state "whiteLevel", action: "setWhiteLevel"
		}
		valueTile("refresh", "capability.refresh", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'Refresh', action: 'refresh'
		}
		main("switch")
		details("switch", "whiteLevelLabel", "setWhiteLevel", "refresh")
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
	state.savedWhite = "00"
	state.savedRGB = "000000"
	state.savedRGBW = "00000000"
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
	logInfo("Default fade speed set to ${state.defFadeSpeed}")
	updateDataValue("driverVersion", driverVer())

	refresh()
}


//	===== Commands and Parse Returns =====
def on() {
	logDebug("on")
	def rgbw = state.savedRGBW
	sendPostCmd("/api/rgbw/set",
				"""{"rgbw":{"desiredColor":"${rgbw}","fadeSpeed":${state.defFadeSpeed}}}""",
				"commandParse")
}
def off() {
	logDebug("off")
	sendGetCmd("/s/00000000", "commandParse")
	sendPostCmd("/api/rgbw/set",
				"""{"rgbw":{"desiredColor":"00000000","fadeSpeed":${state.defFadeSpeed}}}""",
				"commandParse")
}
def setWhiteLevel(whiteLevel) {
	logDebug("setWhite: ${whiteLevel})")
	whiteLevel = (whiteLevel * 2.55).toInteger()
	def wHex = Integer.toHexString(whiteLevel).padLeft(2, '0')
	def rgbw = state.savedRGB + wHex
	sendPostCmd("/api/rgbw/set",
				"""{"rgbw":{"desiredColor":"${rgbw}","fadeSpeed":${state.defFadeSpeed}}}""",
				"commandParse")
}

def setLevel(level) {
	logDebug("setLevel: level = ${level})")
	setRgbColor([hue: device.currentValue("hue"),
			  saturation: device.currentValue("saturation"),
			  level: level])
}

def setHue(hue) {
	logDebug("setHue:  hue = ${hue}")
	setColor([hue: hue.toInteger(),
			  saturation: device.currentValue("saturation"),
			  level: device.currentValue("level")])
}
def setSaturation(saturation) {
	logDebug("setSaturation: saturation = ${saturation}")
	setColor([hue: device.currentValue("hue"),
			  saturation: saturation.toInteger(),
			  level: device.currentValue("level")])
}

def setColor(color) {
	logDebug("setColor: ${color}")
    def level = device.currentValue("level")
    if (level == 0) { level = 10 }
    if (!color.level) {
	    setRgbColor([hue: color.hue, saturation: color.saturation, level: device.currentValue("level")])
    } else {
	    setRgbColor(color)
    }
}
def setRgbColor(color) {
	logDebug("setRgbColor:  color = ${color}")
	def hue = (0.5 + color.hue).toInteger()
	def saturation = (0.5 + color.saturation).toInteger()
    def level = (color.level).toInteger()
	def rgbw = colorUtil.hsvToHex(hue, saturation, level).substring(1,7).toLowerCase()
	rgbw += state.savedWhite
	def fadeSpeed = state.defFadeSpeed
	if (state.tempFade != null) {
		fadeSpeed = state.tempFade
		state.tempFade = null
	}
	sendPostCmd("/api/rgbw/set",
				"""{"rgbw":{"desiredColor":"${rgbw}","fadeSpeed":${fadeSpeed}}}""",
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
	logDebug("commandParse: ${cmdResponse}")
	def hexDesired = cmdResponse.rgbw.desiredColor.toUpperCase()
	if (hexDesired == state.savedRGBW && device.currentValue("switch") == "on") {
		return
	} else if (hexDesired == "00000000") {
		sendEvent(name: "switch", value: "off")
		sendEvent(name: "hue", value: 0)
		sendEvent(name: "saturation", value: 0)
		sendEvent(name: "level", value: 0)
		def color = ["hue": 0, "saturation": 0, "level": 0]
		sendEvent(name: "color", value: color)
		sendEvent(name: "RGB", value: "000000")
		sendEvent(name: "whiteLevel", value: whiteLevel)
	} else {
		sendEvent(name: "switch", value: "on")
		state.savedRGBW = hexDesired
	}
	
	state.savedRGB = hexDesired[0..5]
	sendEvent(name: "RGB", value: hexDesired[0..5])
    def hsv = colorUtil.hexToHsv(hexDesired[0..5])
	sendEvent(name: "hue", value: hsv[0])
	sendEvent(name: "saturation", value: hsv[1])
	sendEvent(name: "level", value: hsv[2])
	def color = ["hue": hsv[0], "saturation": hsv[1], "level": hsv[2]]
	sendEvent(name: "color", value: color)
    
	state.savedWhite = hexDesired[6..7]
	def whiteLevel = Integer.parseInt(hexDesired[6..7],16)
	whiteLevel = (0.5 + whiteLevel/2.55).toInteger()
	sendEvent(name: "whiteLevel", value: whiteLevel)
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
	logDebug("sendPostCmd: ${command} // ${body} // ${action}")
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