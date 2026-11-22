// sendpan - a mono send placed across a bus's channels
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// A mono in added to the bus, times level, at pos: -1 the bus's first
// channel, 1 its last, 0 half way - never round the back from the last to
// the first. Two channels are an equal power pan (Pan2); more are PanAz,
// with width, how many channels the sound lies over, from 2 (next door
// only) to all of them (a wide one does leak from the first channel to the
// last). Below 2 PanAz drops out between channels - silence at pos 0 on
// four - so the knob starts at 2. On a 1-channel
// bus it is a plain send, without pos. A control bus is written the same
// way, at control rate. The output is in, mono, untouched.
//
// With voices, each voice is placed at its own pos, with its own width and
// level, all added to the same bus; the output is the voices, untouched.
//
// The bus is picked as send's is, in the drop-down on the module's face or
// with NdefRack.make(\sendpan, bus: \quad); it sends whether or not the
// module plays, and no bus writes nowhere.
(
	category: \buses,
	help: "a mono send across a bus's channels: in is added at pos (-1 the first channel, 1 the last; width past two channels), times level, and passed on untouched - pick the bus in the drop-down; with voices, each at its own pos",
	about: "A mono send placed across the channels of a bus: pos moves it from the first channel to the last, as a pan across a multichannel bus. The input passes on untouched. With voices, each is placed on its own.",
	params: (
		in: "the mono sound to send; also leaves at the output untouched",
		level: "how much is added to the bus; 0 sends nothing",
		pos: "where on the bus it lands: -1 the first channel, 1 the last, 0 half way; not there on a 1-channel bus",
		width: "how many channels the sound spreads over, 2 neighbours only up to all; only on buses of 3 channels or more"
	),
	writes: true,
	bus: { |index, channels, rate, n = 1| {
		var kr = rate == \control;
		var in = if (kr) { \in.kr(0 ! n, spec: [-1, 1]) } { \in.ar(0 ! n) };
		var level = \level.kr(1 ! n, spec: [0, 2, \amp]);
		var pos = if (channels > 1) { \pos.kr(0 ! n, spec: \pan) };
		var width = if (channels > 2) { \width.kr(2 ! n, spec: [2, channels]) };
		if (index.isNil) { in } {
			// its own fade: an Out here is outside the Ndef's envelope
			var env = EnvGate.new(i_level: 0, doneAction: 2, curve: if (kr) { \lin } { \sin },
				// deleted, the group goes exactly fadeTime later: a control bus
				// must be at 0 by then, or it keeps the last step
				fadeTime: if (kr) { (\fadeTime.kr(0.02) - (2 * ControlDur.ir)).max(0) });
			// with orientation 0, channel k (from 0) is at PanAz's 2k / n
			var at = (pos ? 0 + 1) * (channels - 1) / channels;
			var sig = case
				{ channels == 1 } { in }
				{ channels == 2 } { if (kr) { Pan2.kr(in, pos) } { Pan2.ar(in, pos) } }
				{ kr } { PanAz.kr(channels, in, at, 1, width, 0) }
				{ PanAz.ar(channels, in, at, 1, width, 0) };
			// each voice on its own place, the voices added together
			sig = if (n > 1 and: { channels > 1 }) {
				(sig * level).flop.collect(_.sum)
			} {
				if (channels > 1) { sig * level } { (sig * level).asArray.sum }
			};
			if (kr) { Out.kr(index, sig * env) } { Out.ar(index, sig * env) };
			in * env
		}
	} }
)
