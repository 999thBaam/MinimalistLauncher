#!/bin/bash
echo "Sending test notifications..."

# 1. Simulate Chat Explosion (Test Stacking)
# Note: ADB notifications all come from 'com.android.shell', so they will STACK together.

~/Library/Android/sdk/platform-tools/adb shell cmd notification post -S bigtext -t "WhatsApp" "id1" "Mom: Where are you?"
sleep 0.2
~/Library/Android/sdk/platform-tools/adb shell cmd notification post -S bigtext -t "WhatsApp" "id2" "Mom: Pick up milk"
sleep 0.2
~/Library/Android/sdk/platform-tools/adb shell cmd notification post -S bigtext -t "WhatsApp" "id3" "Group: Dinner tonight?"

# 2. Gmail Stack
~/Library/Android/sdk/platform-tools/adb shell cmd notification post -S bigtext -t "Gmail" "id4" "Work: Meeting at 3 PM"
sleep 0.2
~/Library/Android/sdk/platform-tools/adb shell cmd notification post -S bigtext -t "Gmail" "id5" "Work: Project Update"
sleep 0.2
~/Library/Android/sdk/platform-tools/adb shell cmd notification post -S bigtext -t "Gmail" "id6" "HR: Holiday Calendar"

echo "Sent 2 Stacks (WhatsApp & Gmail). Test Expansion!"
