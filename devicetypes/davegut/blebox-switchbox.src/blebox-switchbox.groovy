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
	definition (name: "bleBox switchBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.switch") {
		capability "Switch"
        capability "Actuator"
        capability "Refresh"
        capability "Health Check"
	}
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action: "switch.off", 
                	icon: "st.switches.switch.on", backgroundColor: "#00a0dc", nextState: "off"
				attributeState "off", label:'${name}', action: "switch.on", 
                	icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "on"
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
		input ("refreshInterval", "enum", title: "Device Refresh Interval (minutes)", 
			   options: ["1", "5", "15", "30"])
		input ("shortPoll", "number",title: "Fast Polling Interval ('0' = DISABLED)")
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
		//	Update device name on manual installation to standard name
		sendGetCmd("/api/device/state", "setDeviceName")
	}

	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}

	if (shortPoll == null) { state.pollInterval = 0 }
    else { state.pollInterval = shortPoll }
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
	sendGetCmd("/s/1", "commandParse")
}

def off() {
	logDebug("off")
	sendGetCmd("/s/0", "commandParse")
}

def ping() { refresh() }

def refresh() {
	logDebug("refresh")
	sendGetCmd("/api/relay/state", "commandParse")
}

def commandParse(response) {
	def cmdResponse = parseInput(response)
	logDebug("commandParse: cmdResponse = ${cmdResponse}")

	def onOff = "off"
	if (cmdResponse[0].state == 1) { onOff = "on" }
	if (onOff != device.currentValue("switch")) {
		sendEvent(name: "switch", value: onOff)
		logInfo("cmdResponse: switch = ${onOff}")
	}
	if (state.pollInterval > 0) { runIn(state.pollInterval, refresh) }
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