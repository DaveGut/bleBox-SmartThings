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
	definition (name: "bleBox wLightBox",
				namespace: "davegut",
				author: "Dave Gutheinz",
                ocfDeviceType: "oic.d.switch") {
		capability "Switch"
		capability "Actuator"
		capability "Health Check"
		capability "Refresh"
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
		input ("debug", "bool", title: "Enable debug logging")
		input ("descriptionText", "bool", title: "Enable description text logging")
	}
}

def installed() {
	logInfo("Installing...")
	sendEvent(name: "DeviceWatch-Enroll",value: "{\"protocol\": \"LAN\", \"scheme\":\"untracked\", \"hubHardwareId\": \"${device.hub.hardwareID}\"}", displayed: false)
	state.savedLevel = "00000000"
	runIn(1, updated)
}

def updated() {
	logInfo("Updating...")
	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")
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
		sendGetCmd("/api/device/state", "setDeviceName")
		sendGetCmd("/api/rgbw/state", "addChildren")
		logInfo("updated: successfully added children ${getChildDevices()}")
	}
	
	switch(refreshInterval) {
		case "1" : runEvery1Minute(refresh); break
		case "5" : runEvery5Minutes(refresh); break
		case "15" : runEvery15Minutes(refresh); break
		default: runEvery30Minutes(refresh)
	}
	logInfo("Refresh interval set for every ${refreshInterval} minute(s).")
	state.transTime = 1

	updateDataValue("driverVersion", driverVer())
	runIn(5, refresh)
}

def setDeviceName(response) {
	def cmdResponse = parseInput(response)
	logDebug("setDeviceData: ${cmdResponse}")
	device.setName(cmdResponse.device.type)
	logInfo("setDeviceData: Device Name updated to ${cmdResponse.device.type}")
}

def addChildren(response) {
	def cmdResponse = parseInput(response)
	logDebug("addChildren: Adding children for mode = ${mode}")

	def dni = device.getDeviceNetworkId()
	def channel
	def child
	switch(cmdResponse.rgbw.colorMode) {
		case "1":
			channel = "rgbw"
			addChild("wLightBox Rgbw", "${dni}-1", "${device.displayName} Rgbw", channel)
			break
		case "2":
			channel = "rgb"
			addChild("wLightBox Rgb", "${dni}-1", "${device.displayName} Rgb", channel)
			break
		case "3":
			channel = "ch1"
			addChild("wLightBox Mono", "${dni}-1", "${device.displayName} Ch1", channel)
			channel = "ch2"
			addChild("wLightBox Mono", "${dni}-2", "${device.displayName} Ch2", channel)
			channel = "ch3"
			addChild("wLightBox Mono", "${dni}-3", "${device.displayName} Ch3", channel)
			channel = "ch4"
			addChild("wLightBox Mono", "${dni}-4", "${device.displayName} Ch4", channel)
			break
		case "4":
			channel = "rgb"
			addChild("wLightBox Rgb", "${dni}-1", "${device.displayName} Rgb", channel)
			channel = "ch4"
			addChild("wLightBox Mono", "${dni}-2", "${device.displayName} White", channel)
			break
		case "5":
			channel = "ct1"
			addChild("wLightBox Ct", "${dni}-1", "${device.displayName} Ct1", channel)
			break
		case "6":
			channel = "ct1"
			addChild("wLightBox Ct", "${dni}-1", "${device.displayName} Ct1", channel)
			channel = "ct2"
			addChild("wLightBox Ct", "${dni}-2", "${device.displayName} Ct2", channel)
			break
		default: 
			logWarn("addChildren: No channel detected in message from device: ${cmdResponse}")
			break
	}
	return
}

def addChild(type, dni, label, channel) {
	logDebug("addChild: ${type} / ${dni} / ${label} / ${channel}")
	def hub = location.hubs[0]
	def hubId = hub.id
	try {
		addChildDevice("davegut", "bleBox ${type}", "${dni}", hubId, [
			"name": type, "label": label, "data": ["channel": channel], isComponent: false])
	} catch (error) {
		logWarn("addChild: failed. Error = ${error}")
		return
	}
	logInfo("addChild: Added child ${type} / ${dni} / ${label} / ${channel}")
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

def childCommand(channel, chLevel, transTime = state.transTime) {
	logDebug("parseChildInput: ${channel}, ${level}, ${transTime}")
	def rgbwNow = state.savedLevel
	switch (channel) {
		case "rgbw":
			setRgbw(chLevel, transTime)
			break
		case "rgb":
			setRgbw(chLevel + rgbwNow[6..7], transTime)
			break
		case "ch1":
			setRgbw(chLevel + rgbwNow[2..7], transTime)
			break
		case "ch2":
			setRgbw(rgbwNow[0..1] + chLevel + rgbwNow[4..7], transTime)
			break
		case "ch3":
			setRgbw(rgbwNow[0..3] + chLevel + rgbwNow[6..7], transTime)
			break
		case "ch4":
			setRgbw(rgbwNow[0..5] + chLevel, transTime)
			break
		case "ct1":
			setRgbw(chLevel + rgbwNow[4..7], transTime)
			break
		case "ct2":
			setRgbw(rgbwNow[0..3] + chLevel, transTime)
			break
		default:
			setRgbw(rgbwNow, transTime)
	}
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
    def onOff = "off"
	if (hexDesired != "00000000") {
		state.savedLevel = hexDesired
        onOff = "on"
	}
	sendEvent(name: "switch", value: "${onOff}")
    logInfo("commandParse: Device is ${onOff}")

	def children = getChildDevices()
	children.each { it.parseReturnData(hexDesired) }
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