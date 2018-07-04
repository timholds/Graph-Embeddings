var data_file = 'data/cluster_decay.csv';

var year = { start: "1950", end: "2020", step: "5"};

var maxImpactValue = 5;

var maxCircleRadius = 12;

// fields titles (note: viz assumes Other as the last title)
// To produce this list, count papers grouped by field and take top 8 fields
var fos = ['Biology', 'Computer Science', 'Econometrics', 'Psychology',
       'Chemistry', 'Classical mechanics', 'Materials Science',
       'Mathematical optimization', 'Other'];

// offsets for cluster centers coordinates (in px)
// - Clusters are equally distributed in a 3x3 grid but based on the data,
// some clusters might need more space than others
// - Start at [0, 0, 0] and tweak till it looks right
var offset = {
 row: [-30, 0, 30],  // row 1, 2, 3
 col: [-30, 0, 40],  // col 1, 2, 3
};

// offsets for cluster titles coordinates (in px)
// - Start at [0, 0, 0] and tweak till it looks right
var titleOffset = {
  row: [-140, -100, -70], // row 1, 2, 3
  col: [-50, 0, 50],  // col 1, 2, 3
};


d3.select('#nRadius-value')
  .text(year.start);

d3.select('#nRadius')
  .attr('min', year.start)
  .attr('max', year.end)
  .attr('step', year.step)
  .attr('value', year.start);
