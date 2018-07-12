var data_file = 'data/decayed_pub_D.csv';

var year = { start: "1950", end: "2020", step: "5"};

var maxImpactValue = 5;

var maxCircleRadius = 13;

var radiusScale = d3.scaleSqrt();


// Set the metric to use for color:
//   data file must have year fields (e.g. 1950, 1955, ...)
//   with the specified suffix (e.g. 1950_D, 1955_D).
//   min and max are used for color interpolation

// Color: impactValue
// var color = {
//   suffix: "",
//   scale: d3.scaleSequential()
//     .interpolator(d3.interpolatePuBuGn),  // Sequential: from 0 to max values
//   min: 0
// };

// Color: derivative
var color = {
  suffix: "_D",
  scale: d3.scaleSequential()
    .interpolator(d3.interpolateRdYlGn),     // Diverging: from negative to positive values
  min: -1,
  max: 1
};


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

// Publisher titles (note: viz assumes Other as the last title)
// To produce this list, count papers grouped by Publisher and take top 8 fields
var pubs = ['IEEE', 'Cambridge University Press', 'American Institute of Physics',
       'McGraw-Hill', 'Oxford University Press', 'Wiley', 'Academic Press',
       'Elsevier', 'Other'];


d3.select('#nRadius-value')
  .text(year.start);

d3.select('#nRadius')
  .attr('min', year.start)
  .attr('max', year.end)
  .attr('step', year.step)
  .attr('value', year.start);
