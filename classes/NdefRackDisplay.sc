// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackDisplay - the live picture in a scope or a spectrum module
//
// A scope or spectrum module is an Ndef that passes its input straight
// through to its own bus; the picture is not the Ndef's business. The
// window gives the module one of these, which runs a small synth of its
// own reading that bus into a Buffer, and reads the Buffer back:
//
//   \scope     the samples after the first channel rises through zero,
//              time seconds wide, one trace per channel - free-running
//              when nothing crosses zero for a quarter of a second
//   \spectrum  a Hann-windowed 2048-point FFT, read back as dB per bin,
//              falling slowly so a peak can be read
//   \probe     the window's own: a short scope of every channel of any
//              audio module, up to 16 - the preview over a cable
//              (NdefRackGUI), made while the mouse is on it
//
//   d = NdefRackDisplay(\scope, NdefRack.at(\scope));
//   d.poll({ d.data.postln });     // the latest, when it arrives
//   d.free;
//
// Nothing here touches the Ndef: no node map entry, nothing in the code
// export or a patch. It reads through InFeedback, so where the Ndef sits
// in the node tree does not matter. A boot gives it a new buffer, and the
// tree being rebuilt - after a boot, after cmd-period - a new synth.
// =====================================================================

NdefRackDisplay {
	classvar <frames = 512, <fftSize = 2048;
	classvar <probeFrames = 128, <>probeTime = 0.03;

	var <kind, <module, <server, <data;
	var buf, synth, pending = false, pendingSince = 0, lastSpeed, smooth;
	var bootFunc, treeFunc, quitFunc, starts = 0, freed = false;

	*new { |kind, module| ^super.newCopyArgs(kind.asSymbol, module).init }

	*kinds { ^#[\scope, \spectrum] }

	init {
		server = module.proxy.server;
		// the old buffer went with the old server; a fresh one comes at the tree
		bootFunc = { buf = nil; synth = nil; pending = false };
		treeFunc = { synth = nil; this.prStart };
		quitFunc = { buf = nil; synth = nil; pending = false };
		ServerBoot.add(bootFunc, server);
		ServerTree.add(treeFunc, server);
		ServerQuit.add(quitFunc, server);
		this.prStart;
	}

	isRunning { ^synth.notNil }

	// how many traces it reads: a scope up to 8, a probe up to 16
	channels { ^this.prChannels }

	// Ask for the latest picture. action is called, from the OSC thread,
	// once it is in data. One request at a time; one lost on the way is
	// given up on after a second.
	poll { |action|
		var now = Main.elapsedTime;
		if (pending and: { (now - pendingSince) < 1 }) { ^this };
		if (buf.isNil or: { synth.isNil } or: { server.serverRunning.not }) { ^this };
		this.prFollowKnobs;
		pending = true;
		pendingSince = now;
		buf.getn(0, this.prReadSize, { |vals|
			pending = false;
			data = this.prDecode(vals);
			action.value(this);
		});
	}

	// how many seconds across a scope draws, and how far up it reaches:
	// the module's own time and gain controls - a probe's are its own
	time { ^if (kind == \probe) { probeTime } { (module.value(\time) ? 0.02).max(0.0005) } }

	gain { ^if (kind == \probe) { 1 } { (module.value(\gain) ? 1).max(0.001) } }

	// the dB at the bottom of a spectrum: the module's floor control
	floor { ^(module.value(\floor) ? -90).min(-6) }

	// the frequency of spectrum bin i of data, which starts at bin 1
	binFreq { |i| ^(i + 1) * this.prSampleRate / fftSize }

	free {
		freed = true;
		ServerBoot.remove(bootFunc, server);
		ServerTree.remove(treeFunc, server);
		ServerQuit.remove(quitFunc, server);
		if (server.serverRunning) {
			synth !? { synth.free };
			buf !? { buf.free };
		};
		synth = nil;
		buf = nil;
	}

	// ---------------------------------------------------------------- private

	prChannels {
		^switch(kind,
			\scope, { (module.numChannels ? 1).clip(1, 8) },
			\probe, { (module.numChannels ? 1).clip(1, 16) },
			{ 1 }
		)
	}

	prFrames { ^if (kind == \probe) { probeFrames } { frames } }

	prReadSize { ^if (kind == \spectrum) { fftSize } { (this.prFrames + 1) * this.prChannels } }

	prSampleRate { ^server.sampleRate ?? { server.options.sampleRate } ? 48000 }

	// In a routine of its own, never the caller's: the window starts displays
	// from its frame, which must not stop to wait for the server - a reply that
	// never came would freeze the window. Only the latest start makes a synth,
	// and none once the display is freed.
	prStart {
		var n = this.prChannels;
		var index = module.proxy.index;
		var start;
		if (freed or: { server.serverRunning.not } or: { index.isNil } or: { module.isAudio.not }) { ^this };
		starts = starts + 1;
		start = starts;
		Routine({
			var def = this.prDef(n);
			if (freed.not) {
				buf ?? {
					buf = if (kind == \spectrum) {
						Buffer.alloc(server, fftSize, 1)
					} {
						Buffer.alloc(server, this.prFrames + 1, n)
					};
				};
				def.send(server);
				server.sync;
				if (freed.not and: { start == starts } and: { buf.notNil }) {
					lastSpeed = nil;
					synth = Synth.tail(RootNode(server), def.name, [\in, index, \buf, buf.bufnum]);
				};
			};
		}).play(SystemClock);
	}

	prFollowKnobs {
		var speed;
		if (kind != \spectrum) {
			speed = this.prFrames / this.time;
			if (speed != lastSpeed) {
				lastSpeed = speed;
				synth !? { synth.set(\speed, speed) };
			};
		};
	}

	prDef { |n|
		var count = this.prFrames;
		^if (kind == \spectrum) {
			SynthDef(\ndefrack_spectrum, { |in, buf|
				FFT(buf, InFeedback.ar(in, 1), 0.5, 1);
			})
		} {
			// a scope and a probe: the same capture, a different length
			SynthDef(("ndefrack_%_%".format(kind, n)).asSymbol, { |in, buf, speed = 25600|
				var sig = InFeedback.ar(in, n);
				var first = sig.asArray[0];
				var rising = (first > 0) * (Delay1.ar(first) <= 0);
				// nothing crossed zero for a while: run free, eight times a second
				var free = Impulse.ar(8) * (Sweep.ar(rising, 1) > 0.25);
				// one capture at a time: Trig1 ignores triggers while it holds, so
				// a wave faster than the window cannot restart it every period
				var capture = Trig1.ar(rising + free, count / speed);
				// count samples from the trigger, then the last slot over and over
				BufWr.ar(sig, buf, Sweep.ar(capture, speed).min(count), 0);
			})
		}
	}

	prDecode { |vals|
		var n, half, db;
		^if (kind != \spectrum) {
			n = this.prChannels;
			// interleaved frames; the last slot is where a finished capture idles
			vals.clump(n).flop.collect { |ch| ch.drop(-1) }
		} {
			// FFT leaves the buffer cartesian - dc, nyquist, then real and
			// imaginary per bin; a full scale sine through a Hann window peaks
			// at a quarter of the FFT size, which is 0 dB here
			half = fftSize div: 2;
			db = (1 .. half - 1).collect { |k|
				(hypot(vals[2 * k], vals[(2 * k) + 1]) / (fftSize / 4)).max(1e-7).ampdb
			};
			smooth = if (smooth.isNil or: { smooth.size != db.size }) { db } {
				db.collect { |v, i| v.max(smooth[i] - 1.5) }
			};
			smooth
		}
	}
}
