/* bubbleChart creation function. Returns a function that will
 * instantiate a new bubble chart given a DOM element to display
 * it in and a dataset to visualize.
 *
 * Organization and style inspired by:
 * https://bost.ocks.org/mike/chart/
 *
 */
function bubbleChart() {
  // Constants for sizing
  var width = 600;
  var height = 600;

  var margin = {top: 20, right: 0, bottom: 0, left: 0};

  // Locations to move bubbles towards, depending
  // on which view mode is selected.
  var center = { x: width / 2, y: height / 2 };

  // @v4 strength to apply to the position forces
  var forceStrength = 0.03;

  // Sizes bubbles based on area.
  // @v4: new flattened scale names.
  var radiusScale = d3.scaleSqrt()
    .range([5, 150])
    .domain([0, 30]);

  // These will be set in create_nodes and create_vis
  var svg = null;
  var bubbles = null;
  var nodes = [];
  var label = null;

  // Charge function that is called for each node.
  // As part of the ManyBody force.
  // This is what creates the repulsion between nodes.
  //
  // Charge is proportional to the diameter of the
  // circle (which is stored in the radius attribute
  // of the circle's associated data.
  //
  // This is done to allow for accurate collision
  // detection with nodes of different sizes.
  //
  // Charge is negative because we want nodes to repel.
  // @v4 Before the charge was a stand-alone attribute
  //  of the force layout. Now we can use it as a separate force!
  function charge(d) {
    return -Math.pow(d.radius, 2.0) * forceStrength;
  }

  // Here we create a force layout and
  // @v4 We create a force simulation now and
  //  add forces to it.
  var simulation = d3.forceSimulation()
    .velocityDecay(0.2)
    .force('x', d3.forceX().strength(forceStrength).x(center.x))
    .force('y', d3.forceY().strength(forceStrength).y(center.y))
    .force('charge', d3.forceManyBody().strength(charge))
    .on('tick', ticked);


  // @v4 Force starts up automatically,
  //  which we don't want as there aren't any nodes yet.
  simulation.stop();


  /*
   * This data manipulation function takes the raw data from
   * the CSV file and converts it into an array of node objects.
   * Each node will store data and visualization values to visualize
   * a bubble.
   *
   * rawData is expected to be an array of data objects, read in from
   * one of d3's loading functions like d3.csv.
   *
   * This function returns the new node array, with a node in that
   * array for each element in the rawData input.
   */
  function createNodes(rawData) {
    // Use map() to convert raw data into node data.
    // Checkout http://learnjsdata.com/ for more on
    // working with data.
    var myNodes = rawData.map(function (d) {
      var node = {
        region: d.region,
        radius: radiusScale(+d["1946"]),
        value: +d["1946"],
        x: Math.random() * 900,
        y: Math.random() * 800
      };

      for (var i = 1946; i <= 2013; i++) {
        node[String(i)] = d[String(i)];
      }

      return node;

    });

    return myNodes;
  }

  /*
   * Main entry point to the bubble chart. This function is returned
   * by the parent closure. It prepares the rawData for visualization
   * and adds an svg element to the provided selector and starts the
   * visualization creation process.
   *
   * selector is expected to be a DOM element or CSS selector that
   * points to the parent element of the bubble chart. Inside this
   * element, the code will add the SVG continer for the visualization.
   *
   * rawData is expected to be an array of data objects as provided by
   * a d3 loading function like d3.csv.
   */
  var chart = function chart(selector, rawData) {
    // convert raw data into nodes data
    nodes = createNodes(rawData);

    // Create a SVG element inside the provided selector
    // with desired size.
    svg = d3.select(selector)
      .append('svg')
      .attr('width', width)
      .attr('height', height);

    // Bind nodes data to what will become DOM elements to represent them.
    bubbles = svg.selectAll('.bubble')
      .data(nodes, function (d) { return d.region; });

    // Create new circle elements each with class `bubble`.
    // There will be one circle.bubble for each object in the nodes array.
    // Initially, their radius (r attribute) will be 0.
    // @v4 Selections are immutable, so lets capture the
    //  enter selection to apply our transtition to below.
    var bubblesE = bubbles.enter().append('circle')
      .classed('bubble', true)
      .attr('r', 0);

      // .on('mouseover', showDetail)
      // .on('mouseout', hideDetail);

    // @v4 Merge the original empty selection and the enter selection
    bubbles = bubbles.merge(bubblesE);


    // Fancy transition to make bubbles appear, ending with the
    // correct radius
    bubbles.transition()
      .duration(2000)
      .attr('r', function (d) { return d.radius; });

    // label is the container div for all the labels that sit on top of
    // the bubbles
    // - remember that we are keeping the labels in plain html and
    //  the bubbles in svg
    label = d3.select(selector).selectAll("#bubble-labels")
      .data([nodes])
      .enter()
      .append("div")
        .attr("id", "bubble-labels");

    createLabels();

    // Set the simulation's nodes to our newly created nodes array.
    // @v4 Once we set the nodes, the simulation will start running automatically!
    simulation.nodes(nodes)
      .force('collision', d3.forceCollide().radius(function(d) {    // avoid overlap
        return d.radius;
      }));

    simulation.restart();
  };

  /*
   * Callback function that is called after every tick of the
   * force simulation.
   * Here we do the acutal repositioning of the SVG circles
   * based on the current x and y values of their bound node data.
   * These x and y values are modified by the force simulation.
   */
  function ticked() {
    bubbles
      .attr('cx', function (d) { return d.x; })
      .attr('cy', function (d) { return d.y; });

    // As the labels are created in raw html and not svg, we need
    // to ensure we specify the 'px' for moving based on pixels
    return label
      .style("left", function (d) { return ((margin.left + d.x) - (d.dx / 2)) + "px"; })
      .style("top", function (d) { return ((margin.top + d.y) - (d.dy / 2)) + "px"; });
  }

  /*
   * Function called on mouseover to display the
   * details of a bubble in the tooltip.
   */
  // function showDetail(d) {
  //   // change outline to indicate hover state.
  //   d3.select(this).attr('stroke', 'black');
  //
  //   var content = '<span class="name">Title: </span><span class="value">' +
  //                 d.name +
  //                 '</span><br/>' +
  //                 '<span class="name">Amount: </span><span class="value">$' +
  //                 addCommas(d.value) +
  //                 '</span><br/>' +
  //                 '<span class="name">Year: </span><span class="value">' +
  //                 d.year +
  //                 '</span>';
  //
  //   tooltip.showTooltip(content, d3.event);
  // }

  /*
   * Hides tooltip
   */
  // function hideDetail(d) {
  //   // reset outline
  //   d3.select(this)
  //     .attr('stroke', d3.rgb(fillColor(d.group)).darker());
  //
  //   tooltip.hideTooltip();
  // }

  // when the input range changes update the circle
  d3.select("#nRadius").on("input", function() {
    update(+this.value);
  });

  function update(year) {
    // adjust the text on the range slider
    d3.select("#nRadius-value").text(year);
    d3.select("#nRadius").property("value", year);

    // update node sizes
    nodes.forEach(function (d) {
      d.radius = radiusScale(+d[String(year)]);
      d.value = +d[String(year)];
    });

    bubbles.transition()
      .duration(1000)
      .attr('r', function (d) { return d.radius; });

    updateLabels();

    simulation.force('collision', d3.forceCollide().radius(function(d) {    // avoid overlap
        return d.radius;
      }))
      .alpha(1).restart();
  }

  function updateLabels() {
    d3.select("#viz").selectAll(".bubble-label-value")
      .text(function (d) { console.log(d.value); return d.value; });


    var label = d3.select("#viz").selectAll(".bubble-label");

    label
      .transition().duration(1000)
      .style("font-size", function (d) { return Math.max(8, radiusScale(d.value / 2)) + "px"; })
      .style("width", function (d) { return (2.5 * radiusScale(d.value)) + "px"; });

    label.append("span")
      .text(function (d) { return d.region; })
      .each(function(d) { d.dx = Math.max(2.5 * radiusScale(d.value), this.getBoundingClientRect().width); })
      .remove();

    label
      .style("width", function (d) { return d.dx + "px"; });

    return label.each(function(d) { d.dy = this.getBoundingClientRect().height; });
  }

  // ---
  // updateLabels is more involved as we need to deal with getting the sizing
  // to work well with the font size
  // ---
  function createLabels() {
    // as in updateNodes, we use idValue to define what the unique id for each data
    // point is
    label = label.selectAll(".bubble-label").data(nodes, function (d) { return d.region; });

    // labels are anchors with div's inside them
    // labelEnter holds our enter selection so it
    // is easier to append multiple elements to this selection
    label = label.enter().append("a")
      .attr("class", "bubble-label")
      .attr("href", function (d) {
        return "#" + encodeURIComponent(d.region);
      });


    label.append("div")
      .attr("class", "bubble-label-name")
      .text(function (d) { return d.region; });

    label.append("div")
      .attr("class", "bubble-label-value")
      .text(function (d) { return d.value; });

    // label font size is determined based on the size of the bubble
    // this sizing allows for a bit of overhang outside of the bubble
    // - remember to add the 'px' at the end as we are dealing with
    //  styling divs
    label
      .style("font-size", function (d) { return Math.max(8, radiusScale(d.value / 2)) + "px"; })
      .style("width", function (d) { return (2.5 * radiusScale(d.value)) + "px"; });

    // interesting hack to get the 'true' text width
    // - create a span inside the label
    // - add the text to this span
    // - use the span to compute the nodes 'dx' value
    //  which is how much to adjust the label by when
    //  positioning it
    // - remove the extra span
    label.append("span")
      .text(function (d) { return d.region; })
      .each(function(d) { d.dx = Math.max(2.5 * radiusScale(d.value), this.getBoundingClientRect().width); })
      .remove();

    // reset the width of the label to the actual width
    label
      .style("width", function (d) { return d.dx + "px"; });

    // compute and store each nodes 'dy' value - the
    // amount to shift the label down
    // 'this' inside of D3's each refers to the actual DOM element
    // connected to the data node
    return label.each(function(d) { d.dy = this.getBoundingClientRect().height; });

  }

  // return the chart function from closure.
  return chart;
}

/*
 * Below is the initialization code as well as some helper functions
 * to create a new bubble chart instance, load the data, and display it.
 */

var myBubbleChart = bubbleChart();

/*
 * Function called once data is loaded from CSV.
 * Calls bubble chart function to display inside #viz div.
 */
function display(data) {
  myBubbleChart('#viz', data);
}

/*
 * Helper function to convert a number into a string
 * and add commas to it to improve presentation.
 */
// function addCommas(nStr) {
//   nStr += '';
//   var x = nStr.split('.');
//   var x1 = x[0];
//   var x2 = x.length > 1 ? '.' + x[1] : '';
//   var rgx = /(\d+)(\d{3})/;
//   while (rgx.test(x1)) {
//     x1 = x1.replace(rgx, '$1' + ',' + '$2');
//   }
//
//   return x1 + x2;
// }

// Load the data.
d3.csv('data/conflict.csv').then(display);
