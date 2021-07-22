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
10.10.10	1.1.01	Combined drivers and updated to match other platform.
					Note:  If device is does not have tile, the tilt command is ignored with a debug message.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.1.01" }
metadata {
	definition (name: "bleBox shutterBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.blind",
                vid: "generic-shade") {
		capability "Window Shade"
		capability "Refresh"
        capability "Switch Level"
        capability "Actuator"
        command "setPosition", ["NUMBER"]
        attribute "position", "number"
		command "stop"
		command "setTilt", ["NUMBER"]
		attribute "tilt", "number"
        capability "Health Check"
	}

    tiles(scale: 2) {
        multiAttributeTile(name:"windowShade", type: "generic", width: 6, height: 4) {
            tileAttribute("device.windowShade", key: "PRIMARY_CONTROL") {
                attributeState "open", label: 'Open', action: "close", 
                	icon: "https://static.thenounproject.com/png/2169292-200.png", 
                    backgroundColor: "#00A0DC", nextState: "closing"
                attributeState "closed", label: 'Closed', action: "open", 
                	icon: "https://static.thenounproject.com/png/144291-200.png", 
                    backgroundColor: "#ffffff", nextState: "opening"
                attributeState "partially open", label: 'Partially open', action: "close", 
                	icon: "https://static.thenounproject.com/png/229594-200.png", 
                    backgroundColor: "#d45614", nextState: "closing"
                attributeState "opening", label: 'Opening', action: "stop", 
                	icon: "https://static.thenounproject.com/png/229594-200.png", 
                    backgroundColor: "#00A0DC", nextState: "partially open"
                attributeState "closing", label: 'Closing', action: "stop", 
                	icon: "https://static.thenounproject.com/png/229594-200.png", 
                    backgroundColor: "#ffffff", nextState: "partially open"
            }
			tileAttribute ("device.position", key: "SLIDER_CONTROL") {
				attributeState "position", label: 'Position: ${currentValue}%', action: "setPosition"
			}
        }
        valueTile("tiltLabel", "device.level", width: 2, height: 1) {
            state "tilt", label: '', icon: "https://static.thenounproject.com/png/1899991-200.png"
        }
        controlTile("tiltSliderControl", "device.tilt", "slider", width:2, height: 1) {
            state "tilt", action:"setTilt"
        }
        valueTile("2x2", "", width: 2, height: 2)
        standardTile("refresh", "device.refresh", decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "windowShade"
        details(["windowShade",
        		 "tiltLabel", "2x2", "refresh",
                 "tiltSliderControl"])
    }
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}

def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
	updated()
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

	updateDataValue("driverVersion", driverVer())

	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
	logInfo("Refresh interval set for every ${refreshInterval} minute(s).")
	refresh()
}


//	===== Commands and updating state =====
def open() {
	logDebug("open")
	def percentage = device.currentValue("position").toInteger()
	setPosition(percentage)
}

def close() {
	logDebug("close")
	sendGetCmd("/s/d", "commandParse")
}

def stop() {
	logDebug("stop")
	sendGetCmd("/api/shutter/state", "stopParse")
}

def stopParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("stopParse: cmdResponse = ${cmdResponse}")
	def stopPosition = cmdResponse.shutter.currentPos.position
	setPosition(stopPosition.toInteger())
}

def setPosition(percentage) {
	logDebug("setLevel:  ${percentage}")
	sendGetCmd("/s/p/${percentage}", "commandParse")
}

def setTilt(percentage) {
	if (getDataValue("mode") != "tilt") {
		logDebug("setTilt: not supported for device mode.")
		return
	}
	logDebug("setTilt: percentage = ${percentage}")
	sendGetCmd("/s/t/${percentage}", "commandParse")
}

def presetPosition(value) {
	logDebug("presetPosition not supported")
}

def ping() { refresh() }

def refresh() {
	logDebug("refresh")
	sendGetCmd("/api/shutter/extended/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: cmdResponse = ${cmdResponse}")
	def shutter = cmdResponse.shutter
	def windowShade
	switch (shutter.state) {
		case 0:
			windowShade = "closing"
			break
		case 1:
			windowShade = "opening"
			break
		case 2:
			windowShade = "partially open"
			break
		case 3:
			windowShade = "closed"
			break
		case 4:
			windowShade = "open"
			break
		default:
			windowShade = "unknown"
	}
	sendEvent(name: "position", value: shutter.currentPos.position)
	if (getDataValue("mode") == "tilt") {
		sendEvent(name: "tilt", value: shutter.currentPos.tilt)
	}
	sendEvent(name: "windowShade", value: windowShade)
	logInfo("commandParse: position = ${shutter.currentPos.position}")
	if(shutter.currentPos != shutter.desiredPos) { runIn(5, refresh) }
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