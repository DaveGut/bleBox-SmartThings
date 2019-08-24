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
	definition (name: "bleBox gateBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.garagedoor") {
		capability "Door Control"
        command "altControl"
		capability "Refresh"
        capability "Health Check"
	}
	tiles(scale: 2) {
		standardTile("doorCtrl", "device.door", width: 6, height: 4, decoration: "flat") {
    		state "open", label: '${currentValue}', action: "close", 
            	icon: "st.switches.door.open", backgroundColor: "#e86d13"
    		state "closed", label: '${currentValue}', action: "open", 
            	icon: "st.switches.door.closed", backgroundColor: "#ffffff"
    		state "unknown", label: '${currentValue}', action: "close", 
            	icon: "st.switches.door.open", backgroundColor: "#e86d13"
        }
		standardTile("altCtrl", "altControl", width: 4, height: 2, decoration: "flat") {
			state "default", label: "Alternate Control", action: "altControl"
		}
		standardTile("refresh", "capability.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Refresh", action: "refresh.refresh"
		}
         main(["doorCtrl"])
        details(["doorCtrl", "altCtrl", "refresh"])
    }
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging")
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
	}

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}

	updateDataValue("driverVersion", driverVer())

	refresh()
}


//	===== Commands and Parse Returns =====
def open() {
	logDebug("open")
	sendGetCmd("/s/p", "commandParse")
    runIn(20, refresh)
}
def close() {
	logDebug("close")
	sendGetCmd("/s/p", "commandParse")
    runIn(20, refresh)
}
def altControl() {
	logDebug("secondary")
	sendGetCmd("/s/s", "commandParse")
    runIn(20, refresh)
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
	if(response.status != 200 || response.body == null) {
		logWarn("parseInput: Command generated an error return: ${response.status} / ${response.body}")
		return
	}
	def jsonSlurper = new groovy.json.JsonSlurper()
	def cmdResponse = jsonSlurper.parseText(response.body)

	logDebug("refreshParse: response = ${cmdResponse}")
	def position = cmdResponse.currentPos
	def doorState = "unknown"
	if (position == 100) { doorState = "open" }
	else if (position == 0) { doorState = "closed" }
	sendEvent(name: "door", value: doorState)
	log.info "${device.label} refreshResponse: door = ${doorState}"
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


//	===== Utility Methods =====
def logInfo(msg) {
	if (descriptionText != false) { log.info "${device.label} ${driverVer()}:   ${msg}" }
}
def logDebug(msg){
	if(debug == true) { log.debug "${device.label} ${driverVer()}:   ${msg}" }
}
def logWarn(msg){ log.warn "${device.label} ${driverVer()}:    ${msg}" }

//	end-of-file