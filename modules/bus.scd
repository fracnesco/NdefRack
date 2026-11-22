// bus - what is on a bus: one of BusManager's, by its name, or an index
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// A bus read as a module: an audio bus at audio rate, a control bus at
// control rate, as many channels as the bus has, times gain. Pick the bus in
// the drop-down on the module's face - BusManager's buses, or a bus index of
// your own, at the width and rate you give it; in code, NdefRack.make(\bus,
// bus: \reverb) or NdefRack.make(\bus, bus: 16, channels: 2). A BusManager
// bus is followed by its name: when BusManager moves it - a reboot, a layout
// loaded, the name added again at another width - the module goes with it. A
// name BusManager does not know is silence until it does; a bus taken away
// leaves the module reading where it was, the way BusManager's other modules
// stay where they were routed. An index of your own stays where it is.
//
// An audio bus is read with InFeedback, as it is, wherever what writes it
// runs: one block late when that runs after the module, never silent.
//
// To write to a bus: send, throw, sendpan, replace, blend, insert.
(
	category: \buses,
	help: "a bus - one of BusManager's by name, or an index of your own: what is on it, at its rate and width, times gain - pick it in the drop-down",
	about: "Reads what is on a bus as a module - one of BusManager's by name, or an index of your own - at the bus's rate and width. Pick it in the drop-down; use it to bring sends, returns or other parts of your setup into the rack.",
	params: (
		gain: "how loud the bus is read: 1 as it is"
	),
	bus: { |index, channels, rate| {
		var sig = case
			{ index.isNil } { DC.ar(0) }
			{ rate == \control } { In.kr(index, channels) }
			{ InFeedback.ar(index, channels) };
		sig * \gain.kr(1, spec: [0, 4, \amp])
	} }
)
