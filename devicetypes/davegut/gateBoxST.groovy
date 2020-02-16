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
10.10.10	1.1.01	Combined drivers and updated to match other platform.
					Note:  If device is non-dimming, the level command is ignored with a debug message.
02.16.20	1.1.02	Added username and password as an optional Authorization Code.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "1.1.02" }
metadata {
	definition (name: "bleBox gateBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.garagedoor") {
		capability "Momentary"
		capability "Contact Sensor"
		capability "Refresh"
        capability "Health Check"
	}
	tiles(scale: 2) {
		standardTile("doorCtrl", "device.contact", width: 6, height: 4, decoration: "flat") {
    		state "open", label: '${currentValue}', action: "close",
            	icon: "st.switches.door.open", backgroundColor: "#e86d13"
    		state "closed", label: '${currentValue}', action: "push",
            	icon: "st.switches.door.closed", backgroundColor: "#ffffff"
    		state "unknown", label: '${currentValue}', action: "push",
            	icon: "st.switches.door.open", backgroundColor: "#e86d13"
        }
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
        main(["doorCtrl"])
        details(["doorCtrl", "refresh"])
    }
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("reverseSense", "bool", title: "Reverse reported Open and Close Status.", defaultValue: false)
		input ("cycleTime", "number",title: "Nominal Door Cycle Time (seconds)",
			   defaultValue: 30)
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"], defaultValue: "30")
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
		input ("authLogin", "text", title: "Optional Auth login")
		input ("authPassword", "text", title: "Auth password")
	}
}
def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
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
		//	Update device name on manual installation to standard name
		sendGetCmd("/api/device/state", "setDeviceName")
	}

	if (!getDataValue("mode")) {
		sendGetCmd("/api/gate/state", "setGateType")
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

def setDeviceName(response) {
	def cmdResponse = parseInput(response)
	logDebug("setDeviceData: ${cmdResponse}")
	device.setName(cmdResponse.device.type)
	logInfo("setDeviceData: Device Name updated to ${cmdResponse.device.type}")
}

def setGateType(response) {
	def cmdResponse = parseInput(response)
	logDebug("setGateType: <b>${cmdResponse}")
	if (cmdResponse == "error") { return }
	def mode
	switch(cmdResponse.gateType) {
		case "0": mode = "slidingDoor"; break
		case "1": mode = "garageDoor"; break
		case "2": mode = "overDoor"; break
		case "3": mode = "door"; break
		default: mode = "notSet"
	}
	updateDataValue("mode", mode)
	logInfo("setGateType: Gate Type set to ${mode}")
}


//	===== Commands and Parse Returns =====
def push() {
	logDebug("push: currently ${device.currentValue("contact")}")
	sendGetCmd("/s/p", "commandParse")
	runIn(cycleTime.toInteger(), refresh)
}

def ping() {
	logDebug("ping")
    refresh()
}

def refresh() {
	logDebug("refresh")
	sendGetCmd("/api/gate/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: ${cmdResponse}")
	if (cmdResponse.gate) { cmdResponse = cmdResponse.gate }
	def closedPos = 0
	if (reverseSense == true) { closedPos = 100 }
	def contact = "open"
	if (cmdResponse.currentPos == closedPos) { contact = "closed" }
	sendEvent(name: "contact", value: contact)
}

//	===== Communications =====
def getAuthorizationHeader() {
		def encoded = "${authLogin}:${authPassword}".bytes.encodeBase64()
		return "Basic ${encoded}"	
}

private sendGetCmd(command, action){
	logDebug("sendGetCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "GET",
    				  path: command,
                      headers: [
                          Host: "${getDataValue("deviceIP")}:80",
						  Authorization: getAuthorizationHeader()
                      ]]
	sendHubCommand(new physicalgraph.device.HubAction(parameters, null, [callback: action]))
}
private sendPostCmd(command, body, action){
	logDebug("sendPostCmd: ${command} // ${getDataValue("deviceIP")} // ${action}")
	def parameters = [method: "POST",
					  path: command,
					  body: body,
					  headers: [
						  Host: "${getDataValue("deviceIP")}:80",
						  Authorization: getAuthorizationHeader()
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