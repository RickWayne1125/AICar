/*
 * Copyright 2014 Akexorcist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.blackwalnutlabs.angels.sendclient.bluetooth

object BluetoothState {
    const val STATE_NONE = 0
    const val STATE_LISTEN = 1
    const val STATE_CONNECTING = 2
    const val STATE_CONNECTED = 3
    const val STATE_NULL = -1
    const val MESSAGE_STATE_CHANGE = 1
    const val MESSAGE_READ = 2
    const val MESSAGE_WRITE = 3
    const val MESSAGE_DEVICE_NAME = 4
    const val MESSAGE_TOAST = 5
    const val REQUEST_CONNECT_DEVICE = 384
    const val REQUEST_ENABLE_BT = 385
    const val DEVICE_NAME = "device_name"
    const val DEVICE_ADDRESS = "device_address"
    const val TOAST = "toast"
    const val DEVICE_ANDROID = true
    const val DEVICE_OTHER = false

    @JvmField
    var EXTRA_DEVICE_ADDRESS = "device_address"
}