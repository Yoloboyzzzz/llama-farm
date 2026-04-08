// All STL processing runs in the browser — no server calls.

const SNAP = 1e-6;         // vertex welding tolerance (model units)
const NORMAL_DOT = 0.9998; // cos(~1.1°) — coplanarity threshold

export interface MeshInfo {
  faceGroups: Int32Array;
  numGroups: number;
  triVertIds: number[][];
  verts: number[][];
  triNormals: number[][];
}

function snapKey(x: number, y: number, z: number): string {
  const s = 1 / SNAP;
  return `${Math.round(x * s)},${Math.round(y * s)},${Math.round(z * s)}`;
}

export function computeFaceGroups(positions: Float32Array): MeshInfo {
  const numTris = positions.length / 9;

  // 1. Build welded vertex list and per-triangle data
  const vertMap = new Map<string, number>();
  const verts: number[][] = [];

  function vertId(x: number, y: number, z: number): number {
    const k = snapKey(x, y, z);
    if (vertMap.has(k)) return vertMap.get(k)!;
    const id = verts.length;
    verts.push([x, y, z]);
    vertMap.set(k, id);
    return id;
  }

  const triVertIds: number[][] = new Array(numTris);
  const triNormals: number[][] = new Array(numTris);

  for (let i = 0; i < numTris; i++) {
    const b = i * 9;
    const ax = positions[b],   ay = positions[b+1], az = positions[b+2];
    const bx = positions[b+3], by = positions[b+4], bz = positions[b+5];
    const cx = positions[b+6], cy = positions[b+7], cz = positions[b+8];

    const ux = bx-ax, uy = by-ay, uz = bz-az;
    const vx = cx-ax, vy = cy-ay, vz = cz-az;
    let nx = uy*vz - uz*vy;
    let ny = uz*vx - ux*vz;
    let nz = ux*vy - uy*vx;
    const nl = Math.sqrt(nx*nx + ny*ny + nz*nz);
    if (nl > 0) { nx /= nl; ny /= nl; nz /= nl; }

    triNormals[i] = [nx, ny, nz];
    triVertIds[i] = [vertId(ax,ay,az), vertId(bx,by,bz), vertId(cx,cy,cz)];
  }

  // 2. Build edge → triangle adjacency
  const edgeMap = new Map<string, number[]>();

  for (let i = 0; i < numTris; i++) {
    const [v0, v1, v2] = triVertIds[i];
    const edges: [number, number][] = [
      [Math.min(v0,v1), Math.max(v0,v1)],
      [Math.min(v1,v2), Math.max(v1,v2)],
      [Math.min(v0,v2), Math.max(v0,v2)],
    ];
    for (const [a, b] of edges) {
      const k = `${a}_${b}`;
      if (!edgeMap.has(k)) edgeMap.set(k, []);
      edgeMap.get(k)!.push(i);
    }
  }

  // 3. BFS grouping — coplanar + edge-adjacent
  const faceGroups = new Int32Array(numTris).fill(-1);
  let numGroups = 0;

  for (let seed = 0; seed < numTris; seed++) {
    if (faceGroups[seed] !== -1) continue;
    const group = numGroups++;
    faceGroups[seed] = group;
    const queue = [seed];

    while (queue.length > 0) {
      const ti = queue.pop()!;
      const [n0, n1, n2] = triNormals[ti];
      const [v0, v1, v2] = triVertIds[ti];

      const adjEdges: [number, number][] = [
        [Math.min(v0,v1), Math.max(v0,v1)],
        [Math.min(v1,v2), Math.max(v1,v2)],
        [Math.min(v0,v2), Math.max(v0,v2)],
      ];

      for (const [a, b] of adjEdges) {
        const neighbors = edgeMap.get(`${a}_${b}`) ?? [];
        for (const nbr of neighbors) {
          if (faceGroups[nbr] !== -1) continue;
          const [nn0, nn1, nn2] = triNormals[nbr];
          if (Math.abs(n0*nn0 + n1*nn1 + n2*nn2) < NORMAL_DOT) continue;
          faceGroups[nbr] = group;
          queue.push(nbr);
        }
      }
    }
  }

  return { faceGroups, numGroups, triVertIds, verts, triNormals };
}

// SVG constants
const CANVAS_W     = 600;
const CANVAS_H     = 300;
const STROKE_W     = 0.1;
const STROKE_COLOR = "rgb(255,0,0)";

export function generateSVG(groupIndex: number, meshInfo: MeshInfo): string | null {
  const { faceGroups, triVertIds, verts, triNormals } = meshInfo;

  const groupTris: number[] = [];
  for (let i = 0; i < faceGroups.length; i++) {
    if (faceGroups[i] === groupIndex) groupTris.push(i);
  }
  if (groupTris.length === 0) return null;

  let nx = 0, ny = 0, nz = 0;
  for (const ti of groupTris) {
    nx += triNormals[ti][0];
    ny += triNormals[ti][1];
    nz += triNormals[ti][2];
  }
  const nl = Math.sqrt(nx*nx + ny*ny + nz*nz);
  nx /= nl; ny /= nl; nz /= nl;

  let rx: number, ry: number, rz: number;
  if (Math.abs(nx) < 0.9) { rx=1; ry=0; rz=0; } else { rx=0; ry=1; rz=0; }
  let ux = ny*rz - nz*ry, uy = nz*rx - nx*rz, uz = nx*ry - ny*rx;
  const ul = Math.sqrt(ux*ux + uy*uy + uz*uz);
  ux /= ul; uy /= ul; uz /= ul;
  const vvx = ny*uz - nz*uy, vvy = nz*ux - nx*uz, vvz = nx*uy - ny*ux;

  function project(vid: number): [number, number] {
    const [x, y, z] = verts[vid];
    return [x*ux + y*uy + z*uz, x*vvx + y*vvy + z*vvz];
  }

  const edgeCount = new Map<string, number>();
  for (const ti of groupTris) {
    const [v0, v1, v2] = triVertIds[ti];
    for (const [a, b] of [[v0,v1],[v1,v2],[v2,v0]] as [number,number][]) {
      const k = `${Math.min(a,b)}_${Math.max(a,b)}`;
      edgeCount.set(k, (edgeCount.get(k) ?? 0) + 1);
    }
  }

  const adj = new Map<number, Set<number>>();
  for (const [k, count] of edgeCount) {
    if (count !== 1) continue;
    const sep = k.indexOf('_');
    const a = parseInt(k.slice(0, sep), 10);
    const b = parseInt(k.slice(sep + 1), 10);
    if (!adj.has(a)) adj.set(a, new Set());
    if (!adj.has(b)) adj.set(b, new Set());
    adj.get(a)!.add(b);
    adj.get(b)!.add(a);
  }

  if (adj.size === 0) return null;

  const loops: number[][] = [];
  const visited = new Set<number>();
  for (const startVid of adj.keys()) {
    if (visited.has(startVid)) continue;
    const loop: number[] = [];
    let prev = -1;
    let cur  = startVid;
    while (!visited.has(cur)) {
      visited.add(cur);
      loop.push(cur);
      let nextVid = -1;
      for (const n of adj.get(cur)!) {
        if (n !== prev && !visited.has(n)) { nextVid = n; break; }
      }
      prev = cur;
      cur  = nextVid;
      if (cur === -1) break;
    }
    if (loop.length >= 3) loops.push(loop);
  }

  if (loops.length === 0) return null;

  let minU = Infinity, minV = Infinity, maxU = -Infinity, maxV = -Infinity;
  for (const loop of loops) {
    for (const vid of loop) {
      const [u, v] = project(vid);
      if (u < minU) minU = u;  if (u > maxU) maxU = u;
      if (v < minV) minV = v;  if (v > maxV) maxV = v;
    }
  }

  const shapeW  = maxU - minU;
  const shapeH  = maxV - minV;
  const offsetX = (CANVAS_W - shapeW) / 2 - minU;
  const offsetY = (CANVAS_H + shapeH) / 2 + minV;

  const pathElements = loops.map(loop => {
    const pts = loop.map(vid => {
      const [u, v] = project(vid);
      return `${(u + offsetX).toFixed(6)},${(-v + offsetY).toFixed(6)}`;
    });
    const d = `M ${pts[0]} ` + pts.slice(1).map(p => `L ${p}`).join(' ') + ' Z';
    return `  <path d="${d}" fill="none" stroke="${STROKE_COLOR}" stroke-width="${STROKE_W}"/>`;
  });

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    `<svg xmlns="http://www.w3.org/2000/svg"`,
    `     width="${CANVAS_W}mm" height="${CANVAS_H}mm"`,
    `     viewBox="0 0 ${CANVAS_W} ${CANVAS_H}">`,
    ...pathElements,
    `</svg>`,
  ].join('\n');
}
