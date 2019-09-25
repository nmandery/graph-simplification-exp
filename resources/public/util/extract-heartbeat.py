
import soundfile as sf # v0.10.2

# file downloaded from 
# https://upload.wikimedia.org/wikipedia/commons/7/75/Heartbeat.ogg
data, samplerate = sf.read('/tmp/Heartbeat.ogg')

# downsample to reduce the amount of data
target_samplerate = 150
i = 0
offset = 0
while offset < len(data):
    print(f"{i};{data[offset]}")
    i += 1
    offset = int(i * samplerate / target_samplerate)