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
09.20.19	1.2.01.	Initial Parent - Child driver release.
					Added link to Application that will check/update IPs if the communications fail.
10.01.19	1.3.01. Updated error handling.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.3.01" }

metadata {
	definition (name: "bleBox wLightBox Rgbw",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.light") {
		capability "Light"
		capability "Switch"
		capability "Actuator"
		capability "Switch Level"
		capability "Color Control"
		capability "Health Check"
        capability "Refresh"
        command "setRed", ["NUMBER"]
        attribute "red", "string"
        command "setGreen", ["NUMBER"]
        attribute "green", "string"
        command "setBlue", ["NUMBER"]
        attribute "blue", "string"
		command "setWhite", ["NUMBER"]
		attribute "white", "string"
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
		valueTile("redLabel", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'Red'
		}
		valueTile("greenLabel", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'Green'
		}
		valueTile("blueLabel", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'Blue'
		}
		valueTile("whiteLabel", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: 'White'
		}
		valueTile("2x1", "default", inactiveLabel: false, decoration: "flat", height: 1, width: 2) {
			state "default", label: ''
		}
		controlTile("setRed", "device.red", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..255)") {
			state "default", action: "setRed"
		}
		controlTile("setGreen", "device.green", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..255)") {
			state "default", action: "setGreen"
		}
		controlTile("setBlue", "device.blue", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..255)") {
			state "default", action: "setBlue"
		}
		controlTile("setWhite", "device.white", "slider", width: 2, height: 1,
        	inactiveLabel: false, range: "(0..270)") {
			state "default", action: "setWhite"
		}
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
		main("switch")
		details("switch",
        		"redLabel", "greenLabel", "blueLabel",
                "setRed", "setGreen", "setBlue",
                "whiteLabel", "2x1", "refresh",
                "setWhite")
	}
	preferences {
		input ("fadeSpeed", "number", title: "Default Transition time (0 - 60 seconds)")
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
    if (fadeSpeed) { state.defTransTime = 1000 * fadeSpeed }
    else { state.defTransTime = 1000 }
    state.transTime = state.defTransTime
	updateDataValue("driverVersion", driverVer())
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	refresh()
}

//	===== Commands and Parse Returns =====
def on() {
	logDebug("on: ${state.savedLevel}")
	parent.childCommand(getDataValue("channel"), state.savedRgbw, state.transTime)
}

def off() {
	logDebug("off")
	parent.childCommand(getDataValue("channel"), "00000000", state.transTime)
}

def setRed(red) {
	logDebug("setRed: ${red})")
	red = Integer.toHexString(red).padLeft(2, '0')
    def rgbw = red + state.savedRgbw[2..7]
	parent.childCommand(getDataValue("channel"), rgbw, state.transTime)
}

def setGreen(green) {
	logDebug("setGreen: ${green})")
	green = Integer.toHexString(green).padLeft(2, '0')
    def rgbw = state.savedRgbw[0..1] + green + state.savedRgbw[4..7]
	parent.childCommand(getDataValue("channel"), rgbw, state.transTime)
}

def setBlue(blue) {
	logDebug("setBlue: ${blue})")
	blue = Integer.toHexString(blue).padLeft(2, '0')
    def rgbw = state.savedRgbw[0..3] + blue + state.savedRgbw[6..7]
	parent.childCommand(getDataValue("channel"), rgbw, state.transTime)
}

def setWhite(white) {
	logDebug("setWhite: ${white})")
	white = Integer.toHexString(white).padLeft(2, '0')
    def rgbw = state.savedRgbw[0..5] + white
	parent.childCommand(getDataValue("channel"), rgbw, state.transTime)
}

def setLevel(level, fadeSpeed = state.transTime) {
	logDebug("setLevel: level = ${level})")
    state.transTime = fadeSpeed
    if (level == 0) { off() }
    else {
		setColor([hue: device.currentValue("hue"),
			  saturation: device.currentValue("saturation"),
			  level: level])
    }
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
    def level
    if (!color.level) { level = device.currentValue("level").toInteger() }
    else { level = color.level.toInteger() }
	def hue = (0.5 + color.hue).toInteger()
	def saturation = (0.5 + color.saturation).toInteger()
	def rgb = colorUtil.hsvToHex(hue, saturation, level).substring(1,7).toLowerCase()
    def rgbw = rgb + state.savedRgbw[6..7]
	parent.childCommand(getDataValue("channel"), rgbw, state.transTime)
    state.transTime = state.defTransTime
}

def ping() { refresh() }

def refresh() {
	logDebug("refresh.")
	parent.refresh()
}

def parseReturnData(hexDesired) {
	logDebug("parseReturnData: ${hexDesired}")
	if (hexDesired == "00000000") {
		sendEvent(name: "switch", value: "off")
		logInfo("Device is off.")
	} else {
		sendEvent(name: "switch", value: "on")
		state.savedRgbw = hexDesired
	    def hsv = colorUtil.hexToHsv(hexDesired[0..5])
		sendEvent(name: "hue", value: hsv[0])
		sendEvent(name: "saturation", value: hsv[1])
		sendEvent(name: "level", value: hsv[2])
		def color = ["hue": hsv[0], "saturation": hsv[1], "level": hsv[2]]
		sendEvent(name: "color", value: color)
		def red = Integer.parseInt(hexDesired[0..1],16)
		sendEvent(name: "red", value: red)
		def green = Integer.parseInt(hexDesired[2..3],16)
		sendEvent(name: "green", value: green)
		def blue = Integer.parseInt(hexDesired[4..5],16)
		sendEvent(name: "blue", value: blue)
		def white = Integer.parseInt(hexDesired[6..7],16)
		sendEvent(name: "white", value: white)
        logInfo("parseReturnData: Device is on. Color: ${color}, white: ${whiteLevel}")
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