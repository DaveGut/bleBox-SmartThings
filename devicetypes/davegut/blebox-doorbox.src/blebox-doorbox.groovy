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
	definition (name: "bleBox doorBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.door") {
		capability "Door Control"
        capability "Refresh"
        capability "Health Check"
	}
	tiles(scale: 2) {
		multiAttributeTile(name: "door", type: "door", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.door", key: "PRIMARY_CONTROL") {
				attributeState "closed", label:'${currentValue}', action: "open", 
                	icon: "st.Home.home2", backgroundColor: "#ffffff", nextState: "open"
				attributeState "open", label:'${currentValue}', action: "open",
                	icon: "st.Home.home2", backgroundColor: "#e86d13", nextState: "closed"
				attributeState "unknown", label:'${currentValue}', action: "open",
                	icon: "st.Home.home2", backgroundColor: "#e86d13", nextState: "closed"
			}
		}
        valueTile("2x4", "", width: 4, height: 2)
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
		main("door")
		details("door", "2x4", "refresh")
	}
	preferences {
		input ("device_IP", "text", title: "Manual Install Device IP")
		input ("doorSwitch", "bool", title: "Door Switch Installed")
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("fastPoll", "enum",title: "Enable fast polling", 
			   options: ["No", "1", "2", "3", "4", "5", "10", "15"])
		input ("debug", "bool", title: "Enable debug logging")
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
		case "30" : runEvery30Minutes(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery1Minute(refresh)
	}

	if (!fastPoll || fastPoll =="No") { state.pollInterval = "0" }
	else { state.pollInterval = fastPoll }
	updateDataValue("driverVersion", driverVer())

	refresh()
}


//	===== Commands and Parse Returns =====
def open() {
	logDebug("open")
	sendGetCmd("/s/p", "commandParse")
}
def close() {
	logDebug("close:  Close has no meaning relative to this device.")
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
    if (doorSwitch == true) {
   		doorState = "closed"
        if (cmdResponse.currentPos == 100) {
        	doorState = "open"
            runIn(6, refresh)
        } else if (state.pollInterval != "0") {
			runIn(state.pollInterval.toInteger(), quickPoll)
		}
    }
	sendEvent(name: "door", value: doorState)
	logInfo("commandParse: door set to state ${doorState}")
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