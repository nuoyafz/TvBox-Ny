import struct, zlib, sys

def load(p):
    d = open(p, 'rb').read()
    pos = 8
    idat = b''
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', d[pos+8:pos+18])
        if typ == b'IDAT':
            idat += d[pos+8:pos+8+ln]
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * 4 if ct == 6 else w * 3
    prev = bytearray(stride)
    rows = []
    p2 = 0
    for y in range(h):
        f = raw[p2]; p2 += 1
        line = bytearray(raw[p2:p2+stride]); p2 += stride
        if f == 1:
            for i in range(3, stride):
                line[i] = (line[i] + line[i-3]) & 0xff
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xff
        elif f == 3:
            for i in range(stride):
                a = line[i-3] if i >= 3 else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xff
        elif f == 4:
            for i in range(stride):
                a = line[i-3] if i >= 3 else 0
                b = prev[i]
                c = prev[i-3] if i >= 3 else 0
                pp = a + b - c
                pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xff
        prev = line
        rows.append(bytes(line))
    return w, h, b''.join(rows)

w, h, p0 = load(sys.argv[1])
_, _, p1 = load(sys.argv[2])
_, _, p2 = load(sys.argv[3])
def diff(a, b):
    return sum(1 for x, y in zip(a, b) if abs(x - y) > 12)
print('diff t0-t1:', diff(p0, p1), ' t1-t2:', diff(p1, p2))
