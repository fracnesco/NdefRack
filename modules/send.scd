// send - a send to a bus: one of BusManager's, by its name, or an index
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// What comes in at in is added to the bus, times level, and passed on at
// the output untouched - a send on the way: wave -> send -> mixer, and the
// same sound on the reverb's bus. Pick the bus in the drop-down on the
// module's face, as bus's: an audio bus is written at audio rate, a control
// bus at control rate, as many channels as the bus has. In code,
// NdefRack.make(\send, bus: \reverb) or NdefRack.make(\send, bus: 16,
// channels: 2). A BusManager bus is followed by its name; a name BusManager
// does not know, a bus it takes away, and no bus at all write nowhere - in
// still goes through - until it has a bus of that name again.
//
// On a 2-channel audio bus a stereo cable fills both sides and a mono one
// feeds both - the left is normalled to the right while the right is
// silent, as reverb's input is. Wider buses go channel to channel. So do
// voices: past the bus's width they are dropped - mix them first.
//
// It adds: two sends on one bus sum. It sends whether or not the module
// plays - play and its volume are the module's own output, not the send;
// level is the send. Sent to a hardware output it is heard. Moved to
// another bus, or deleted, it fades out of the old one in the Ndef's
// fadeTime (measured 2026-11-20), and a control bus is left at 0.
(
	category: \buses,
	help: "a send: in is added to a bus - one of BusManager's by name, or an index - times level, and passed on untouched; it sends whether or not the module plays - pick the bus in the drop-down",
	about: "Adds its input to a bus - picked by name or index in the drop-down - and passes it on untouched, like a send on a mixer. Use it to feed a shared reverb or delay bus. It sends even when the module is not playing.",
	params: (
		in: "the sound or modulation to send; also leaves at the output untouched",
		level: "how much is added to the bus; 0 sends nothing"
	),
	writes: true,
	bus: { |index, channels, rate| {
		var kr = rate == \control;
		var in = if (kr) { \in.kr(0 ! channels, spec: [-1, 1]) } { \in.ar(0 ! channels) };
		var level = \level.kr(1, spec: [0, 2, \amp]);
		if (index.isNil) { in } {
			// its own fade: an Out here is outside the Ndef's envelope, and
			// would double on every new source
			var env = EnvGate.new(i_level: 0, doneAction: 2, curve: if (kr) { \lin } { \sin },
				// deleted, the group goes exactly fadeTime later: a control bus
				// must be at 0 by then, or it keeps the last step
				fadeTime: if (kr) { (\fadeTime.kr(0.02) - (2 * ControlDur.ir)).max(0) });
			var sig = if (kr.not and: { channels == 2 }) {
				[in[0], Select.ar(Amplitude.kr(in[1], 0.01, 2) > 0.0001, [in[0], in[1]])]
			} { in };
			if (kr) { Out.kr(index, sig * level * env) } { Out.ar(index, sig * level * env) };
			in * env
		}
	} }
)
