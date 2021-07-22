/*
===== Blebox SmaartThings Integration Driver

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
		standardTile("refresh", "capability.refresh", width: 2, height: 1, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
        }
		main("switch")
		details(["switch", "refresh"])
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
	state.savedLevel = "00"
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
	parent.childCommand(getDataValue("channel"), state.savedLevel, state.transTime)
}

def off() {
	logDebug("off")
	parent.childCommand(getDataValue("channel"), "0000", state.transTime)
}

def setLevel(level, transTime = state.transTime) {
	logDebug("setLevel: level = ${level})")
	level = (level * 2.55).toInteger()
	level = Integer.toHexString(level).padLeft(2, '0')
	parent.childCommand(getDataValue("channel"), level, transTime)
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
		case "ch1":
			hexLevel = hexDesired[0..1]
			break
		case "ch2":
			hexLevel = hexDesired[2..3]
			break
		case "ch3":
			hexLevel = hexDesired[4..5]
			break
		case "ch4":
			hexLevel = hexDesired[6..7]
			break
		default: return
	}
	if (hexLevel == "00") {
		sendEvent(name: "switch", value: "off")
		logInfo("parseReturnData: Device is off.")
	} else {
		sendEvent(name: "switch", value: "on")
		state.savedLevel = hexLevel
		def level = Integer.parseInt(hexLevel,16)
		level = (0.5 + (level / 2.55)).toInteger()
		sendEvent(name: "level", value: level)
		logInfo("parseReturnData: Device is on. level: ${level}")
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