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
	definition (name: "bleBox wLightBox Ct",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.light") {
		capability "Light"
		capability "Switch"
		capability "Actuator"
		capability "Switch Level"
		capability "Color Temperature"
		capability "Refresh"
		capability "Health Check"
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
				attributeState "level", label: "Brightness: ${currentValue}", action: "switch level.setLevel"
			}
		}
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
		controlTile("colorTempSliderControl", "device.colorTemperature", "slider", width: 4, height: 1,
        	inactiveLabel: false, range: "(2000..9000)") {
			state "colorTemperature", action: "color temperature.setColorTemperature"
		}
		valueTile("colorTemp", "device.colorTemperature", decoration: "flat", height: 1, width: 4) {
			state "colorTemperature", label: 'Color Temp'
		}
		main("switch")
		details("switch", "colorTemp", "refresh", "colorTempSliderControl")
	}
	preferences {
    	input ("ctLow", "number", title: "Low Color Temperature Scale Limit", displayDuringSetup: true)
    	input ("ctHigh", "number", title: "High Color Temperature Scale Limit", displayDuringSetup: true)
		input ("fadeSpeed", "number", title: "Default Transition time (0 - 60 seconds)")
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}

def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
	state.savedLevel = "FF00"
	runIn(2, updated)
}

def updated() {
	logInfo("Updating...")
	unschedule()
    if (!ctLow) { updateDataValue("ctLow", "2700") }
    else { updateDataValue("ctLow", "${ctLow}") }
    if (!ctHigh) { updateDataValue("ctHigh", "6500") }
    else { updateDataValue("ctHigh", "${ctHigh}") }
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
	parent.childCommand(getDataValue("channel"), state.savedLevel, state.transTime)
}

def off() {
	logDebug("off")
	parent.childCommand(getDataValue("channel"), "0000", state.transTime)
}

def setLevel(level, transTime = state.transTime) {
	logDebug("setLevel: ${level})")
	setChannel(device.currentValue("colorTemperature"), level, transTime)
}

def setColorTemperature(ct) {
	logDebug("setColorTemperature: ${ct}")
    def ctHigh = getDataValue("ctHigh").toInteger()
    def ctLow = getDataValue("ctLow").toInteger()
    if (ct <= 100) {
    	ct = ctLow + (0.5 + ct * (ctHigh - ctLow) /100).toInteger()
        logDebug("setColorTemperature: setting CT based on entered percent.")
    }
   	if (ct < ctLow) {
    	ct = ctLow
        logDebug("setColorTemperature: Entered CT below ctLow and reset to ctLow.")
    } else if (ct > ctHigh) {
    	ct = ctHigh
        logDebug("setColorTemperature: Entered CT above ctHigh and reset to ctHigh.")
	}
    setChannel(ct, device.currentValue("level"), state.transTime)
}

def setChannel(ct, level, transTime) {
	logDebug("setChannel: ${ct}K, ${level}%, ${transTime}ms")
    def ctHigh = getDataValue("ctHigh").toInteger()
    def ctLow = getDataValue("ctLow").toInteger()
	def ctMid = ((ctLow + ctHigh) / 2).toInteger()
	def calcFactor = 255 / ((ctHigh - ctLow) * 0.5)
	level = level / 100
	def warmValue
	def coolValue
	if (ct <= ctMid) {
		warmValue = 255
		coolValue = (0.5 + (ct - ctLow) * calcFactor).toInteger()
	} else {
		coolValue = 255
		warmValue = (0.5 + (ctHigh - ct) * calcFactor).toInteger()
	}
	def warm255 = (0.5 + warmValue * level).toInteger()
	def warmHex = Integer.toHexString(warm255).padLeft(2, '0')
	def cool255 = (0.5 + coolValue * level).toInteger()
	def coolHex = Integer.toHexString(cool255).padLeft(2, '0')
	parent.childCommand(getDataValue("channel"), warmHex + coolHex, transTime)
    state.transTime = state.defTransTime
}

def ping() { refresh() }

def refresh() {
	logDebug("refresh.")
	parent.refresh()
}

def parseReturnData(hexDesired) {
	logDebug("parseReturnData: ${hexDesired}")
	def hexLevel
	switch(getDataValue("channel")) {
		case "ct1":
			hexLevel = hexDesired[0..3]
			break
		case "ct2":
			hexLevel = hexDesired[4..7]
			break
		default: return
	}
    def ctHigh = getDataValue("ctHigh").toInteger()
    def ctLow = getDataValue("ctLow").toInteger()
	if (hexLevel == "0000") {
		sendEvent(name: "switch", value: "off")
		logInfo("parseReturnData: Device is Off")
	} else {
		state.savedLevel = hexLevel
		def calcFactor = 255 / ((ctHigh - ctLow) * 0.5)
		def warm255 = Integer.parseInt(hexLevel[0..1],16)
		def cool255 = Integer.parseInt(hexLevel[2..3],16)
		def level = Math.max(cool255, warm255) / 255
		def warmValue = warm255 / level
		def coolValue = cool255 / level
		level = (0.5 + 100 * level).toInteger()		//	level to integer percent
		def ct
		if (coolValue <= warmValue) {
			ct = (ctLow + coolValue / calcFactor).toInteger()
		} else {
			ct = (ctHigh - warmValue / calcFactor).toInteger()
		}
		sendEvent(name: "switch", value: "on")
		sendEvent(name: "level", value: level)
		sendEvent(name: "colorTemperature", value: ct)
		logInfo("parseReturnData: On, color temp: ${ct}K, level: ${level}%")
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