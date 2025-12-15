#!/bin/bash

# Get list of devices
devices=($(adb devices | grep -v "List" | grep "device" | awk '{print $1}'))

if [ ${#devices[@]} -eq 0 ]; then
    echo "❌ No devices connected"
    exit 1
elif [ ${#devices[@]} -eq 1 ]; then
    echo "✅ Only one device found: ${devices[0]}"
    echo "This device will be used automatically by adb commands"
    exit 0
fi

echo "📱 Multiple devices detected:"
echo ""
adb devices -l
echo ""
echo "To target a specific device, use one of these methods:"
echo ""
echo "1️⃣  Set environment variable (current session):"
echo "   export ANDROID_SERIAL=<device-id>"
echo ""
echo "2️⃣  Use -s flag with adb commands:"
echo "   adb -s <device-id> <command>"
echo ""
echo "3️⃣  Create device-specific tasks in tasks.json"
echo ""
echo "Available device IDs:"
for device in "${devices[@]}"; do
    echo "   • $device"
done
