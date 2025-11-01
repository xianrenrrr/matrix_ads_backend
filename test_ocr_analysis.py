import json

with open('src/main/java/com/example/demo/exmaple.json', 'r') as f:
    data = json.load(f)

result = json.loads(data['Data']['Result'])

# Filter to 3+ character text
y_positions = {}
total_elements = 0
meaningful_elements = 0

for frame in result['frames']:
    for elem in frame['elements']:
        total_elements += 1
        text = elem['text']
        if len(text) >= 3:
            meaningful_elements += 1
            top = elem['textRectangles']['top']
            normalized = (top // 10) * 10
            if normalized not in y_positions:
                y_positions[normalized] = []
            y_positions[normalized].append((frame['timestamp'], text))

# Sort by count
sorted_y = sorted(y_positions.items(), key=lambda x: len(x[1]), reverse=True)

print(f"Total elements: {total_elements}")
print(f"Meaningful elements (3+ chars): {meaningful_elements}")
print(f"\nTop 5 Y positions (meaningful text only):")
print(f"{'Y Position':<12} {'Count':<8} {'% of Meaningful':<16} {'Sample Text'}")
print("-" * 70)
for y, items in sorted_y[:5]:
    pct = (len(items) * 100 / meaningful_elements)
    sample = items[0][1]
    if len(sample) > 30:
        sample = sample[:30] + "..."
    print(f"Y={y:<9} {len(items):<8} {pct:>6.1f}%          '{sample}'")

# Show what will be extracted
best_y = sorted_y[0][0]
print(f"\n\n✅ PREDICTED RESULT:")
print(f"Subtitle region will be Y={best_y}")
print(f"\nText that will be extracted (within ±20px of Y={best_y}):")
extracted = []
for y, items in sorted_y:
    if abs(y - best_y) <= 20:
        for timestamp, text in items:
            extracted.append((timestamp, text))

extracted.sort()
for timestamp, text in extracted[:10]:
    print(f"  t={timestamp}ms: '{text}'")
if len(extracted) > 10:
    print(f"  ... and {len(extracted) - 10} more")
