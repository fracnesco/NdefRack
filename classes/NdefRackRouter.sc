// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackRouter - cables as a flow chart
//
// Geometry only, no window and no server: the window hands over the
// modules' boxes and the cables' ends, and gets back, for each cable, the
// corners of a line of straight runs at right angles.
//
//   r = NdefRackRouter((osc: Rect(20, 20, 190, 120), lpf: Rect(270, 20, 190, 160)));
//   r.routeAll([(key: [\osc, \lpf, \in], from: \osc, to: \lpf,
//       a: Point(210, 46), b: Point(270, 46))]);   // -> key -> [Point, ...]
//
// a is where the cable leaves its output, on the right edge of the module
// it comes from; b where it goes into its input, on the left edge of the
// other. A cable has one of two shapes:
//
//   a ----.              a ---.         .--- b
//         |                   |         |
//         '---- b             '---------'
//     z: [x]                 u: [x1, y, x2]
//
// z, three runs: along a's row, down or up at x, along b's row into the
// input. Only when b is right of a and nothing is in the way. u, five runs:
// out of the output, down or up at x1 to a lane at y, along it, then at x2
// to b's row and in - for a cable running backwards, or forwards past
// modules. The numbers in brackets are the cable's bend: where its middle
// runs are. A bend given with a cable is kept (clipped so the runs leave
// and enter their ports forwards); without one the router finds it: the z
// nearest the input that touches no module, else the u whose lane is
// nearest the two rows - under them rather than over, about as near. Cables from different outputs keep apart on
// shared ground - a vertical run moves over by spacing pixels until it
// is clear - while cables from one output may share a run, so a fan out
// reads as a tree.
// =====================================================================

NdefRackRouter {
	// how far a cable runs out of its output and into its input before it
	// turns; between runs of different cables; around a module
	classvar <>stub = 16, <>spacing = 8, <>clearance = 4;
	// how far a lane keeps from a module; what a lane over the rows costs
	// against one under them, in pixels - a return runs under its row
	classvar <>laneClearance = 14, <>over = 1000;

	// keys, lefts, tops, rights, bottoms: rects side by side - a route
	// asks them thousands of times
	var <rects, verticals, horizontals;
	var keys, lefts, tops, rights, bottoms, lowest, highest;

	*new { |rects| ^super.new.prInit(rects) }

	prInit { |argRects|
		rects = argRects ?? { IdentityDictionary.new };
		verticals = IdentityDictionary.new;
		horizontals = IdentityDictionary.new;
		keys = rects.keys.asArray;
		lefts = keys.collect { |k| rects[k].left };
		tops = keys.collect { |k| rects[k].top };
		rights = keys.collect { |k| rects[k].right };
		bottoms = keys.collect { |k| rects[k].bottom };
		lowest = bottoms.maxItem ? -inf;
		highest = tops.minItem ? inf;
	}

	// ------------------------------------------------------------ answers

	// cables: Events (key:, from:, to:, a:, b:, bend:) - bend optional.
	// Those with a bend are laid first, so the others keep clear of them.
	routeAll { |cables|
		var res = Dictionary.new;
		cables = cables.select { |c| c[\a].notNil and: { c[\b].notNil } };
		(cables.select { |c| c[\bend].notNil } ++ cables.select { |c| c[\bend].isNil }).do { |c|
			res.put(c[\key], this.route(c[\a], c[\b], c[\from], c[\to], c[\bend]))
		};
		^res
	}

	// the corners of one cable, from a to b; it then takes up its ground
	route { |a, b, from, to, bend|
		var pts = (bend !? { this.prFromBend(a, b, bend) }) ?? { this.prAuto(a, b, from, to) };
		this.prOccupy(pts, from);
		^pts
	}

	// the bend of a cable from its corners: [x] for a z, [x1, y, x2] for a u
	*bendOf { |pts|
		^case
		{ pts.size == 4 } { [pts[1].x] }
		{ pts.size == 6 } { [pts[1].x, pts[2].y, pts[4].x] }
	}

	// The bend after the run at index seg (from pts[seg] to pts[seg + 1]) is
	// dragged by delta: a vertical run moves sideways, the lane up or down.
	// The runs out of the output and into the input move the vertical run
	// beside them, since their own row is the port's.
	*dragBend { |pts, seg, delta|
		var bend = this.bendOf(pts);
		bend ?? { ^nil };
		^if (pts.size == 4) {
			[bend[0] + delta.x]
		} {
			switch(seg,
				0, { [bend[0] + delta.x, bend[1], bend[2]] },
				1, { [bend[0] + delta.x, bend[1], bend[2]] },
				2, { [bend[0], bend[1] + delta.y, bend[2]] },
				3, { [bend[0], bend[1], bend[2] + delta.x] },
				4, { [bend[0], bend[1], bend[2] + delta.x] },
				{ bend }
			)
		}
	}

	// the runs of a cable that can be dragged, as indexes into its corners:
	// the middle ones
	*movable { |pts|
		^case
		{ pts.size == 4 } { [1] }
		{ pts.size == 6 } { [1, 2, 3] }
		{ [] }
	}

	// the index of the run of pts nearest point, and how far it is
	*nearestRun { |pts, point|
		var best = 0, dist = inf;
		pts.doAdjacentPairs { |p, q, i|
			var d = this.segmentDistance(point, p, q);
			if (d < dist) { dist = d; best = i };
		};
		^[best, dist]
	}

	*segmentDistance { |p, q1, q2|
		var d = q2 - q1;
		var len2 = (d.x * d.x) + (d.y * d.y);
		var t;
		if (len2 == 0) { ^(p - q1).rho };
		t = ((((p.x - q1.x) * d.x) + ((p.y - q1.y) * d.y)) / len2).clip(0, 1);
		^(p - (q1 + (d * t))).rho
	}

	// corners without repeats and without corners on a straight run - what
	// is drawn
	*simplify { |pts|
		var out = [];
		pts.do { |p| if (out.isEmpty or: { out.last != p }) { out = out.add(p) } };
		^out.select { |p, i|
			i == 0 or: { i == (out.size - 1) } or: {
				var q = out[i - 1], r = out[i + 1];
				((q.x == p.x) and: { p.x == r.x }).not and: { ((q.y == p.y) and: { p.y == r.y }).not }
			}
		}
	}

	// ----------------------------------------------------------- private

	// A bend given: a z only where b is right of a; the runs clipped so the
	// cable leaves its output going right and enters its input going right.
	prFromBend { |a, b, bend|
		var x1, x2, y;
		^case
		{ bend.size == 1 } {
			if ((b.x - a.x) < (2 * spacing)) { nil } {
				x1 = bend[0].clip(a.x + spacing, b.x - spacing);
				[a, Point(x1, a.y), Point(x1, b.y), b]
			}
		}
		{ bend.size == 3 } {
			x1 = bend[0].max(a.x + spacing);
			x2 = bend[2].min(b.x - spacing);
			y = bend[1];
			[a, Point(x1, a.y), Point(x1, y), Point(x2, y), Point(x2, b.y), b]
		}
	}

	// First keeping off other outputs' runs; when no way does, sharing
	// their ground - never a module's.
	prAuto { |a, b, from, to|
		[true, false].do { |lanes|
			(this.prZ(a, b, from, to, lanes) ?? { this.prU(a, b, from, to, lanes) }) !? { |pts| ^pts }
		};
		^this.prULast(a, b)
	}

	// The z with its vertical run nearest the input, in the gap before it,
	// moving left a spacing at a time to the gap after the output.
	prZ { |a, b, from, to, lanes = true|
		var x = b.x - stub - (spacing div: 2);
		if ((b.x - a.x) < (2 * stub)) { ^nil };
		while { x >= (a.x + stub) } {
			// something in the way into the input stays in the way further left
			if (this.prClearH(b.y, x, b.x, [to], from, clearance, false).not) { ^nil };
			if (this.prClearH(a.y, a.x, x, [from], from, clearance, false)
				and: { this.prClearV(x, a.y, b.y, [], from, lanes) }) {
				^[a, Point(x, a.y), Point(x, b.y), b]
			};
			x = x - spacing;
		};
		^nil
	}

	// The u: out of the output, along a lane, into the input. Its lane the
	// clear one nearest the two rows - between them when there is room,
	// else under the modules, over them only when that is much nearer - and
	// its verticals as close to the ports as they come clear for that lane.
	prU { |a, b, from, to, lanes = true|
		var outs = this.prTracks(a.x + stub, 1, a.y, [from], from, lanes);
		var ins = this.prTracks(b.x - stub, -1, b.y, [to], from, lanes);
		if (outs.isEmpty or: { ins.isEmpty }) { ^nil };
		this.prLanesDo(a, b) { |y|
			var x1 = this.prTrackTo(outs, y);
			var x2 = x1 !? { this.prTrackTo(ins, y) };
			if (x2.notNil and: { this.prClearH(y, x1, x2, [], from, laneClearance, lanes) }) {
				^[a, Point(x1, a.y), Point(x1, y), Point(x2, y), Point(x2, b.y), b]
			};
		};
		^nil
	}

	// no way clear at all: under everything
	prULast { |a, b|
		var y = a.y.max(b.y).max(lowest) + (2 * laneClearance);
		var x1 = a.x + stub, x2 = b.x - stub;
		^[a, Point(x1, a.y), Point(x1, y), Point(x2, y), Point(x2, b.y), b]
	}

	// The heights a lane may run at, the nearest first, one by one - the
	// first few are nearly always enough. Between the rows, from the middle
	// out; then down from the lower row and up from the upper, whichever is
	// less out of the way, a lane over the rows costing over.
	prLanesDo { |a, b, func|
		var lo = a.y.min(b.y), hi = a.y.max(b.y), mid = (a.y + b.y) / 2;
		var limit = hi.max(lowest) + 200;
		var top = (lo.min(highest) - 200).max(4);
		// the lanes are top, top + spacing ..: below and above step outwards
		var below = top + (((mid - top) / spacing).roundUp.max(0) * spacing);
		var above = below - spacing;
		// what a lane costs: how far out of the way between the rows, then
		// how far from the middle
		var cost = { |y|
			var out = (y - a.y).abs + (y - b.y).abs + if (y < lo) { over } { 0 };
			(out * 4096) + (y - mid).abs
		};
		var down = below <= limit, up = above >= top;
		var costDown = if (down) { cost.(below) }, costUp = if (up) { cost.(above) };
		while { down or: { up } } {
			if (up.not or: { down and: { costDown <= costUp } }) {
				func.(below);
				below = below + spacing;
				if (below <= limit) { costDown = cost.(below) } { down = false };
			} {
				func.(above);
				above = above - spacing;
				if (above >= top) { costUp = cost.(above) } { up = false };
			}
		}
	}

	// The vertical tracks beside a port at y: eight from x, stepping by dir.
	// Each is [x, top, bottom], how far up and down it is clear from y - found
	// once, where asking every lane for a clear track was most of a route's
	// time in a full rack. One blocked at y itself is left out.
	prTracks { |x, dir, y, skip, from, lanes|
		var res = [];
		8.do { |i|
			var t = x + (i * spacing * dir);
			var top = -inf, bottom = inf;
			var block = { |lo, hi|
				if (hi > y) { bottom = bottom.min(lo) };
				if (lo < y) { top = top.max(hi) };
			};
			keys.size.do { |j|
				if (t > (lefts[j] - clearance) and: { t < (rights[j] + clearance) }
					and: { skip.includes(keys[j]).not }) {
					block.(tops[j] - clearance, bottoms[j] + clearance)
				}
			};
			if (lanes) {
				this.prRunsNear(verticals, t) { |run|
					if (run[3] != from) { block.(run[1], run[2]) }
				}
			};
			// a run starting or ending right at y leaves one side open
			if (top <= y or: { bottom >= y }) { res = res.add([t, top, bottom, y]) };
		};
		^res
	}

	// the first of these tracks clear as far as y, or nil
	prTrackTo { |tracks, y|
		tracks.do { |tr|
			if (if (y >= tr[3]) { y <= tr[2] } { y >= tr[1] }) { ^tr[0] }
		};
		^nil
	}

	// a horizontal run at y from x0 to x1, room away from every module but
	// those in skip - and, with lanes, along no other output's horizontal run
	prClearH { |y, x0, x1, skip, from, room, lanes|
		var l = x0.min(x1), r = x0.max(x1);
		keys.size.do { |i|
			if (y > (tops[i] - room) and: { y < (bottoms[i] + room) }
				and: { r > (lefts[i] - clearance) } and: { l < (rights[i] + clearance) }
				and: { skip.includes(keys[i]).not }) { ^false }
		};
		^lanes.not or: { this.prLaneFree(horizontals, y, l, r, from) }
	}

	// a vertical run at x from y0 to y1 through no module but those in skip,
	// and with lanes along no other output's vertical run
	prClearV { |x, y0, y1, skip, from, lanes = true|
		var t = y0.min(y1), b = y0.max(y1);
		keys.size.do { |i|
			if (x > (lefts[i] - clearance) and: { x < (rights[i] + clearance) }
				and: { b > (tops[i] - clearance) } and: { t < (bottoms[i] + clearance) }
				and: { skip.includes(keys[i]).not }) { ^false }
		};
		^lanes.not or: { this.prLaneFree(verticals, x, t, b, from) }
	}

	// runs: [at, from, to, output], in strips a spacing wide - nothing of
	// another output within a spacing of at, overlapping from..to
	prLaneFree { |runs, at, lo, hi, from|
		this.prRunsNear(runs, at) { |run|
			if (run[3] != from and: { run[2] > lo } and: { run[1] < hi }) { ^false }
		};
		^true
	}

	prRunsNear { |runs, at, func|
		var strip = (at / spacing).floor.asInteger;
		(strip - 1).forBy(strip + 1, 1) { |i|
			runs.at(i) !? { |list|
				list.do { |run| if ((run[0] - at).abs < spacing) { func.(run) } }
			}
		}
	}

	prAddRun { |runs, at, lo, hi, from|
		var strip = (at / spacing).floor.asInteger;
		runs.put(strip, runs.at(strip).add([at, lo, hi, from]));
	}

	prOccupy { |pts, from|
		pts.doAdjacentPairs { |p, q|
			case
			{ p.x == q.x and: { p.y != q.y } } { this.prAddRun(verticals, p.x, p.y.min(q.y), p.y.max(q.y), from) }
			{ p.y == q.y and: { p.x != q.x } } { this.prAddRun(horizontals, p.y, p.x.min(q.x), p.x.max(q.x), from) };
		};
	}
}
