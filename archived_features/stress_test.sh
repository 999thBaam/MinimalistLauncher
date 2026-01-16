#!/bin/bash

# Configuration
INTERVAL=10
COUNT=100
ADB_PATH=~/Library/Android/sdk/platform-tools/adb

echo "🚀 Starting LLM Stress Test: $COUNT notifications at ${INTERVAL}s intervals."
echo "Logs to watch: 'AttentionRouter' and 'MediaPipeIntent'"

# Test Data (Mixed Categories)
# Format: "Title|Text|Expected"
declare -a messages=(
    "Uber|Your driver Mahesh is arriving in 2 minutes.|TASK"
    "Gmail|Security Alert: New login detected from Chrome.|STATE"
    "WhatsApp|Mom: Please call me back when you are free.|MESSAGE"
    "Swiggy|Flat 50% OFF on Pizza Hut! Order Now.|PROMO"
    "Instagram|@sarah_jane mentioned you in a comment.|SOCIAL_MENTION"
    "System|Battery is low (14%). Please charge.|STATE"
    "Slack|DevOps: Deployment to production failed.|TASK"
    "Twitter|Elon Musk posted: Dogecoin to the moon!|SOCIAL"
    "HDFC Bank|Rs 5000.00 debited from a/c **1234.|IMPORTANT"
    "LinkedIn|You appeared in 5 searches this week.|SOCIAL_PROMO"
)

# Loop
for ((i=1; i<=COUNT; i++)); do
    # Pick random message
    rand_idx=$((RANDOM % ${#messages[@]}))
    msg="${messages[$rand_idx]}"
    IFS="|" read -r title text expected <<< "$msg"
    
    # Unique Tag to avoid overwriting (forcing new classification)
    tag="test_tag_$i"
    full_title="$title $i of $COUNT"
    
    echo "[$i/$COUNT] Sending: '$full_title' -> '$text'"
    
    # Execute ADB Command
    # Quote arguments heavily to survive shell passing
    $ADB_PATH shell cmd notification post -t "'$full_title'" "'$tag'" "'$text'"
    
    if [ $i -lt $COUNT ]; then
        sleep $INTERVAL
    fi
done

echo "✅ Stress Test Complete."
