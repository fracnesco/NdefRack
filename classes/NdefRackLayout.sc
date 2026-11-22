// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackLayout - where modules go
//
// Geometry only, no window and no server: the window hands over the
// modules' boxes and the cables, and gets points back.
//
//   l = NdefRackLayout(keys, boxes, NdefRack.cables);
//   l.place(origins, area);     // -> key -> Point, for the modules
//                               //    origins has no place for
//   l.arrange(Point(20, 20));   // -> key -> Point, for all of them
//   l.tidy(keys, Point(20, 20)); // -> key -> Point, for keys, by the
//                               //    layout rules
//
// A box is (size: Point, out: Point, ins: (param: Point), read: Point,
// rate: \audio or \control) - the module's size, its ports and where a
// read goes in, in its own coordinates, and its rate (tidy's main path).
//
// The signal flows left to right. A new module cabled to modules that have
// a place goes right of what feeds it, or left of what it feeds, level
// with them, at the nearest spot that touches nothing - and, among the
// spots about as near, one whose cables cross fewer cables, run through
// fewer modules and do not run backwards. New modules cabled only to each
// other are laid out as a group, the way arrange lays out the rack, and
// the group goes in the first free spot from the top of what is in view;
// a module with no cables at all is a group of one. Modules that have a
// place never move - except in arrange.
//
// arrange: a column per distance from a source - a module with nothing
// patched in stands in the first, a module fed by it in the next; a loop
// is cut where it is found - and each column ordered by the height of
// what it is cabled to, sweeping right then left twice, which uncrosses
// most cables. Modules with no cables go in a row under the columns.
// =====================================================================

NdefRackLayout {
	// between columns; between modules in a column; the room kept around a
	// module that has a place
	classvar <>gap = 60, <>vgap = 20, <>margin = 14;
	// tidy: between rows (the lane returning cables run in); between modules
	// stacked in one column of a row
	classvar <>rowGap = 120, <>stackGap = 60;
	// what a cable running backwards, crossing another, or running through
	// a module costs, in pixels moved from the spot it would like
	classvar <>backwards = 400, <>crossing = 120, <>through = 80;
	classvar offsets;

	var <keys, <boxes, <links, adjacent;

	*new { |keys, boxes, cables|
		^super.newCopyArgs(keys.asArray.select { |k| boxes.at(k).notNil }, boxes).prInit(cables)
	}

	prInit { |cables|
		links = cables.select { |c|
			c[\from] != c[\to] and: { boxes.at(c[\from]).notNil } and: { boxes.at(c[\to]).notNil }
		};
		adjacent = IdentityDictionary.new;
		links.do { |l|
			adjacent.put(l[\from], (adjacent.at(l[\from]) ? []).add(l[\to]));
			adjacent.put(l[\to], (adjacent.at(l[\to]) ? []).add(l[\from]));
		};
	}

	// ------------------------------------------------------------ answers

	place { |origins, area|
		var placed = IdentityDictionary.new, res = IdentityDictionary.new;
		var groups = this.prGroups(keys.reject { |k| origins.at(k).notNil });
		keys.do { |k| origins.at(k) !? { |p| placed.put(k, p) } };
		// cabled to something with a place first: the free groups keep clear of them
		groups.do { |group|
			var left = group.copy;
			if (group.any { |k| this.prNeighbours(k).any { |n| placed.at(n).notNil } }) {
				while { left.notEmpty } {
					var k = this.prMostAnchored(left, placed);
					var p = this.prBest(k, this.prWish(k, placed), placed);
					placed.put(k, p);
					res.put(k, p);
					left.remove(k);
				}
			}
		};
		groups.do { |group|
			var rel, size, corner;
			if (group.every { |k| placed.at(k).isNil }) {
				rel = this.prColumns(group);
				size = Point(0, 0);
				rel.keysValuesDo { |k, p| size = Point(size.x.max(p.x + boxes[k][\size].x), size.y.max(p.y + boxes[k][\size].y)) };
				corner = this.prFreeSpot(size, area, placed);
				rel.keysValuesDo { |k, p|
					placed.put(k, p + corner);
					res.put(k, p + corner);
				};
			}
		};
		^res
	}

	// the cabled modules in columns; modules with no cables in a row under
	// them - in the columns, the sweeps kept a lone module at its first
	// height, at the top, and pushed every cabled one below it
	arrange { |corner|
		var cabled = keys.select { |k| this.prNeighbours(k).notEmpty };
		var lone = keys.reject { |k| this.prNeighbours(k).notEmpty };
		var res = this.prColumns(cabled), width = 0, y = 0, x = 0, rowH = 0;
		res.keysValuesDo { |k, p|
			width = width.max(p.x + boxes[k][\size].x);
			y = y.max(p.y + boxes[k][\size].y + (vgap * 2));
		};
		width = width.max(800);
		lone.do { |k|
			var size = boxes[k][\size];
			if (x > 0 and: { x + size.x > width }) {
				x = 0;
				y = (y + rowH + vgap).roundUp(10).asInteger;
				rowH = 0;
			};
			res.put(k, Point(x, y.roundUp(10).asInteger));
			x = (x + size.x + gap).roundUp(10).asInteger;
			rowH = rowH.max(size.y);
		};
		corner = (corner ? Point(20, 20)).asPoint;
		res.keysValuesDo { |k, p| res.put(k, p + corner) };
		^res
	}

	// how many cables cross another - for checks, and for comparing layouts
	crossings { |origins|
		var segs = this.prSegments(origins, nil), n = 0;
		segs.do { |s, i|
			segs.do { |t, j|
				if (j > i and: { this.prShare(s, t).not } and: { this.class.crosses(s[\a], s[\b], t[\a], t[\b]) }) {
					n = n + 1
				}
			}
		};
		^n
	}

	// do segments ab and cd cross - touching at an end is not crossing
	*crosses { |a, b, c, d|
		// the sign only: Integer products of whole pixels overflow 32 bits
		var side = { |p, q, r| (((q.x - p.x) * (r.y - p.y)) - ((q.y - p.y) * (r.x - p.x))).sign };
		^((side.(c, d, a) * side.(c, d, b)) < 0) and: { (side.(a, b, c) * side.(a, b, d)) < 0 }
	}

	// does segment ab run through rect
	*runsThrough { |a, b, rect|
		^rect.containsPoint(a) or: { rect.containsPoint(b) } or: {
			[
				[rect.leftTop, rect.rightTop], [rect.rightTop, rect.rightBottom],
				[rect.rightBottom, rect.leftBottom], [rect.leftBottom, rect.leftTop]
			].any { |e| this.crosses(a, b, e[0], e[1]) }
		}
	}

	// ------------------------------------------------------------ the graph

	prNeighbours { |k| ^adjacent.at(k) ? [] }

	// the modules cabled together, a group per island, in the order of keys
	prGroups { |set|
		var inSet = IdentitySet.newFrom(set), seen = IdentitySet.new, groups = [];
		set.do { |k|
			var group = [], todo = [k];
			if (seen.includes(k).not) {
				seen.add(k);
				while { todo.notEmpty } {
					var n = todo.removeAt(0);
					group = group.add(n);
					this.prNeighbours(n).do { |m|
						if (inSet.includes(m) and: { seen.includes(m).not }) {
							seen.add(m);
							todo = todo.add(m);
						}
					};
				};
				groups = groups.add(set.select { |m| group.includes(m) });
			};
		};
		^groups
	}

	// how far each module is from a source, cables inside the set only;
	// a loop is cut where it is found
	prDepths { |set|
		var inSet = IdentitySet.newFrom(set);
		var depth = IdentityDictionary.new, inputs = IdentityDictionary.new, visit;
		links.do { |l|
			if (inSet.includes(l[\from]) and: { inSet.includes(l[\to]) }) {
				inputs.put(l[\to], (inputs.at(l[\to]) ? []).add(l[\from]))
			}
		};
		visit = { |key, seen|
			depth.at(key) ?? {
				var d = 0;
				(inputs.at(key) ? []).do { |from|
					var next;
					if (seen.includes(from).not) {
						next = seen.copy;
						next.add(from);
						d = d.max(visit.(from, next) + 1);
					};
				};
				depth.put(key, d);
				d
			}
		};
		set.do { |k| visit.(k, IdentitySet[k]) };
		^depth
	}

	// ------------------------------------------------------------ columns

	// places for a set of modules, from 0@0: a column per depth, each
	// ordered by the height of what it is cabled to
	prColumns { |set|
		var depth = this.prDepths(set);
		var cols = [], xs = [], pos = IdentityDictionary.new, x = 0, top;
		if (set.isEmpty) { ^pos };
		set.do { |k|
			var d = depth.at(k);
			while { cols.size <= d } { cols = cols.add([]) };
			cols[d] = cols[d].add(k);
		};
		cols.do { |col, i|
			xs = xs.add(x);
			x = (x + (col.collect { |k| boxes[k][\size].x }.maxItem ? 0) + gap).roundUp(10).asInteger;
			cols[i] = this.prStack(col, xs[i], pos, nil);
		};
		2.do {
			(cols.size - 1).do { |j|
				var i = j + 1;
				cols[i] = this.prStack(cols[i], xs[i], pos, { |n| depth.at(n).notNil and: { depth.at(n) < i } });
			};
			(cols.size - 1).do { |j|
				var i = cols.size - 2 - j;
				cols[i] = this.prStack(cols[i], xs[i], pos, { |n| depth.at(n).notNil and: { depth.at(n) > i } });
			};
		};
		top = pos.values.collect(_.y).minItem;
		pos.keysValuesDo { |k, p| pos.put(k, Point(p.x, p.y - top)) };
		^pos
	}

	// a column top to bottom, ordered by the mean height of what each module
	// is cabled to on one side (side: a test for a neighbour); each as level
	// with that as it can be without touching the one above. A module with
	// nothing on that side keeps its height. Answers the new order.
	prStack { |col, x, pos, side|
		var bottom;
		var order = col.collect { |k, i|
			var ns = if (side.isNil) { [] } {
				this.prNeighbours(k).select { |n| side.(n) and: { pos.at(n).notNil } }
			};
			var level = case
				{ ns.notEmpty } { ns.collect { |n| pos.at(n).y }.mean }
				{ pos.at(k).notNil } { pos.at(k).y }
				{ 0 };
			[level, i, k]
		};
		order.sort { |a, b| if (a[0] == b[0]) { a[1] < b[1] } { a[0] < b[0] } };
		^order.collect { |e|
			var k = e[2], y = e[0].round(10).asInteger;
			bottom !? { y = y.max((bottom + vgap).roundUp(10).asInteger) };
			pos.put(k, Point(x, y));
			bottom = y + boxes[k][\size].y;
			k
		}
	}

	// --------------------------------------------- one module beside others

	// the one with the most cables to modules that have a place
	prMostAnchored { |left, placed|
		var best, most = -1;
		left.do { |k|
			var n = this.prNeighbours(k).count { |m| placed.at(m).notNil };
			if (n > most) { best = k; most = n };
		};
		^best
	}

	// where a module would like to be: right of what feeds it, else left of
	// what it feeds; level with the mean of both
	prWish { |k, placed|
		var size = boxes[k][\size];
		var froms = [], tos = [], x, y;
		links.do { |l|
			if (l[\to] == k and: { placed.at(l[\from]).notNil }) { froms = froms.add(l[\from]) };
			if (l[\from] == k and: { placed.at(l[\to]).notNil }) { tos = tos.add(l[\to]) };
		};
		x = if (froms.notEmpty) {
			froms.collect { |f| placed.at(f).x + boxes[f][\size].x }.maxItem + gap
		} {
			tos.collect { |t| placed.at(t).x }.minItem - gap - size.x
		};
		y = (froms ++ tos).collect { |n| placed.at(n).y }.mean;
		^Point(x.round(10).asInteger, y.round(10).asInteger)
	}

	// the free spot nearest the wish, as cheap as can be: spots are tried
	// nearest first, and the search stops where the distance alone costs
	// more than the best found
	prBest { |k, wish, placed|
		var size = boxes[k][\size];
		var rects = this.prRects(placed);
		var taken = rects.values.collect { |r| r.insetBy(margin.neg, margin.neg) };
		var segs = this.prSegments(placed, k);
		var best, cost = inf, bottom = 0;
		this.class.prOffsets.do { |o|
			var p, r, c;
			if (o.rho > cost) { ^best };
			p = wish + o;
			if (p.x >= 20 and: { p.y >= 20 }) {
				r = Rect(p.x, p.y, size.x, size.y);
				if (taken.every { |t| t.intersects(r).not }) {
					c = o.rho + this.prCost(k, p, placed, rects, segs);
					if (c < cost) { best = p; cost = c };
				};
			};
		};
		best !? { ^best };
		// nothing free near: below everything
		rects.do { |r| bottom = bottom.max(r.bottom) };
		^Point(wish.x.max(20), (bottom + 30).roundUp(10).asInteger)
	}

	// what the cables of k cost with k at p
	prCost { |k, p, placed, rects, segs|
		var r = Rect(p.x, p.y, boxes[k][\size].x, boxes[k][\size].y);
		var cost = 0;
		links.do { |l|
			var other = if (l[\from] == k) { l[\to] } { if (l[\to] == k) { l[\from] } };
			var a, b;
			if (other.notNil and: { placed.at(other).notNil }) {
				a = this.prOut(l[\from], if (l[\from] == k) { p } { placed.at(l[\from]) });
				b = this.prIn(l, if (l[\to] == k) { p } { placed.at(l[\to]) });
				if (a.x > (b.x - 10)) { cost = cost + backwards };
				segs.do { |s|
					if (s[\from] != other and: { s[\to] != other }
						and: { this.class.crosses(a, b, s[\a], s[\b]) }) {
						cost = cost + crossing
					}
				};
				rects.keysValuesDo { |m, rect|
					if (m != other and: { this.class.runsThrough(a, b, rect) }) { cost = cost + through }
				};
			}
		};
		// cables already there that it would cover
		segs.do { |s| if (this.class.runsThrough(s[\a], s[\b], r)) { cost = cost + through } };
		^cost
	}

	// ------------------------------------------------------------ tidy

	// Places for the modules in set, by the layout rules,
	// from corner: the main path - the longest chain of audio modules, ending
	// where nothing goes on - on one row, a column per stage; an audio module
	// fed by that row and feeding it (a filter beside a filter) under the
	// stage it runs beside; every other part - what the cables between the rest join
	// together - on a row of its own under it, each module right of what feeds
	// it, or left of what it feeds when nothing feeds it. Columns 60 apart,
	// rows rowGap apart, modules in one column stackGap apart. Islands of the
	// set one under the other; modules with no cables in the set in a row
	// under everything. Only cables between modules of the set count.
	tidy { |set, corner|
		var keysIn = keys.select { |k| set.includes(k) };
		var inSet = IdentitySet.newFrom(keysIn);
		var ls = links.select { |l| inSet.includes(l[\from]) and: { inSet.includes(l[\to]) } };
		var succ = IdentityDictionary.new, pred = IdentityDictionary.new;
		var res = IdentityDictionary.new, y, width = 0, x = 0, rowH = 0, lone, cabled;
		ls.do { |l|
			if ((succ.at(l[\from]) ? []).includes(l[\to]).not) { succ.put(l[\from], (succ.at(l[\from]) ? []).add(l[\to])) };
			if ((pred.at(l[\to]) ? []).includes(l[\from]).not) { pred.put(l[\to], (pred.at(l[\to]) ? []).add(l[\from])) };
		};
		corner = (corner ? Point(20, 20)).asPoint;
		y = corner.y;
		cabled = keysIn.select { |k| succ.at(k).notNil or: { pred.at(k).notNil } };
		lone = keysIn.reject { |k| cabled.includes(k) };
		this.prIslands(cabled, succ, pred).do { |island|
			var block = this.prTidyBlock(island, succ, pred), bottom = 0;
			block.keysValuesDo { |k, p|
				res.put(k, p + Point(corner.x, y));
				bottom = bottom.max(p.y + boxes[k][\size].y);
				width = width.max(p.x + boxes[k][\size].x);
			};
			y = (y + bottom + rowGap).roundUp(10).asInteger;
		};
		width = width.max(800);
		lone.do { |k|
			var size = boxes[k][\size];
			if (x > 0 and: { x + size.x > width }) {
				x = 0;
				y = (y + rowH + vgap).roundUp(10).asInteger;
				rowH = 0;
			};
			res.put(k, Point(corner.x + x, y));
			x = (x + size.x + gap).roundUp(10).asInteger;
			rowH = rowH.max(size.y);
		};
		^res
	}

	// the keys joined by cables (succ/pred), an array per island, keys order
	prIslands { |set, succ, pred|
		var seen = IdentitySet.new, res = [];
		set.do { |k|
			var island = [], todo;
			if (seen.includes(k).not) {
				todo = [k];
				seen.add(k);
				while { todo.notEmpty } {
					var n = todo.removeAt(0);
					island = island.add(n);
					((succ.at(n) ? []) ++ (pred.at(n) ? [])).do { |m|
						if (set.includes(m) and: { seen.includes(m).not }) { seen.add(m); todo = todo.add(m) }
					}
				};
				res = res.add(set.select { |m| island.includes(m) });
			}
		};
		^res
	}

	// one island, from 0@0
	prTidyBlock { |island, succ, pred|
		var audio = island.select { |k| (boxes[k][\rate] ? \audio) == \audio };
		var allowed = if (audio.notEmpty) { audio } { island };
		var main = this.prMainPath(island, allowed, succ, pred);
		var col = IdentityDictionary.new, row = IdentityDictionary.new, rest, parts, minCol, xs, rows, y;
		main.do { |k, i| col.put(k, i); row.put(k, 0) };
		// the rest, fed modules first - a module joins after what feeds it
		rest = island.reject { |k| main.includes(k) };
		while { rest.notEmpty } {
			var fed = rest.select { |k| (pred.at(k) ? []).any { |n| col.at(n).notNil } };
			var k = if (fed.notEmpty) { fed.first } {
				rest.detect { |k| (succ.at(k) ? []).any { |n| col.at(n).notNil } } ? rest.first
			};
			var ps = (pred.at(k) ? []).select { |n| col.at(n).notNil };
			var ss = (succ.at(k) ? []).select { |n| col.at(n).notNil };
			col.put(k, case
				{ ps.notEmpty } { ps.collect { |n| col.at(n) }.maxItem + 1 }
				{ ss.notEmpty } { ss.collect { |n| col.at(n) }.minItem - 1 }
				{ 0 });
			rest = rest.reject { |m| m == k };
		};
		// beside the main row: audio, fed by the main path and feeding it
		island.do { |k|
			if (row.at(k).isNil and: { allowed.includes(k) }
				and: { (pred.at(k) ? []).any { |n| main.includes(n) } }
				and: { (succ.at(k) ? []).any { |n| main.includes(n) } }) {
				row.put(k, 0)
			}
		};
		// the parts: what the cables between the others join, a row each, the
		// leftmost first
		parts = this.prIslands(island.select { |k| row.at(k).isNil }, succ, pred);
		parts = parts.sort { |a, b|
			var ca = a.collect { |k| col.at(k) }.minItem, cb = b.collect { |k| col.at(k) }.minItem;
			if (ca == cb) { a.size >= b.size } { ca < cb }
		};
		parts.do { |part, i| part.do { |k| row.put(k, i + 1) } };
		minCol = island.collect { |k| col.at(k) }.minItem;
		island.do { |k| col.put(k, col.at(k) - minCol) };
		// the columns' x: each as wide as its widest module
		xs = [0];
		(island.collect { |k| col.at(k) }.maxItem + 1).do { |c|
			var w = island.select { |k| col.at(k) == c }.collect { |k| boxes[k][\size].x }.maxItem ? 0;
			xs = xs.add((xs.last + w + gap).roundUp(10).asInteger);
		};
		// the rows, one under the other; in a row, a column's modules stacked,
		// the main path's first
		rows = IdentityDictionary.new;
		y = 0;
		(parts.size + 1).do { |r|
			var inRow = island.select { |k| row.at(k) == r }, bottom = y;
			inRow = inRow.select { |k| main.includes(k) } ++ inRow.reject { |k| main.includes(k) };
			inRow.collect { |k| col.at(k) }.asSet.do { |c|
				var top = y;
				inRow.select { |k| col.at(k) == c }.do { |k|
					rows.put(k, Point(xs[c], top));
					top = (top + boxes[k][\size].y + stackGap).roundUp(10).asInteger;
					bottom = bottom.max(top - stackGap);
				}
			};
			y = (bottom + rowGap).roundUp(10).asInteger;
		};
		^rows
	}

	// the longest chain through allowed modules that ends where nothing in the
	// island goes on (or anywhere, when no allowed module is such an end);
	// searched backwards, each module once, at most 20000 steps
	prMainPath { |island, allowed, succ, pred|
		var ends = allowed.select { |k| (succ.at(k) ? []).isEmpty };
		var best = [], steps = 0, walk;
		if (ends.isEmpty) { ends = allowed };
		walk = { |path|
			var ps = (pred.at(path.first) ? []).select { |n| allowed.includes(n) and: { path.includes(n).not } };
			steps = steps + 1;
			if (path.size > best.size) { best = path };
			if (steps < 20000) { ps.do { |n| walk.(path.copy.addFirst(n)) } };
		};
		ends.do { |k| walk.([k]) };
		^best
	}

	// ------------------------------------------------------------ geometry

	prOut { |k, origin| ^origin + boxes[k][\out] }

	// a cable with no param (an Ndef read in a source) goes into the box's
	// read: point - the title's left edge
	prIn { |l, origin|
		var box = boxes[l[\to]];
		^origin + ((box[\ins] ? ()).at(l[\param]) ?? { box[\read] } ? Point(0, 12))
	}

	prRects { |placed|
		var res = IdentityDictionary.new;
		placed.keysValuesDo { |k, p| res.put(k, Rect(p.x, p.y, boxes[k][\size].x, boxes[k][\size].y)) };
		^res
	}

	// the cables between modules that have a place, except those of skip
	prSegments { |placed, skip|
		^links.select { |l|
			l[\from] != skip and: { l[\to] != skip }
				and: { placed.at(l[\from]).notNil } and: { placed.at(l[\to]).notNil }
		}.collect { |l|
			(from: l[\from], to: l[\to],
				a: this.prOut(l[\from], placed.at(l[\from])), b: this.prIn(l, placed.at(l[\to])))
		}
	}

	prShare { |s, t|
		^[s[\from], s[\to]].any { |k| k == t[\from] or: { k == t[\to] } }
	}

	// The first place, in reading order from the top of what is in view,
	// where a box of this size fits without touching a module.
	prFreeSpot { |size, area, placed|
		var taken = this.prRects(placed).values.collect { |r| r.insetBy(margin.neg, margin.neg) };
		var width = area.width.max(size.x + 40);
		var top = area.top.round(10).asInteger + 20, left = area.left.round(10).asInteger + 20;
		var x, y = top;
		while { y < (top + 4000) } {
			x = left;
			while { (x + size.x) < (area.left + width) } {
				var r = Rect(x, y, size.x, size.y);
				if (taken.every { |t| t.intersects(r).not }) { ^Point(x, y) };
				x = x + 30;
			};
			y = y + 30;
		};
		^Point(left, top)
	}

	// steps of 20 pixels out to 800 every way, nearest first
	*prOffsets {
		^offsets ?? {
			offsets = (-40..40).collect { |i| (-40..40).collect { |j| Point(i * 20, j * 20) } }
				.flatten.sort { |a, b| a.rho < b.rho };
			offsets
		}
	}
}
