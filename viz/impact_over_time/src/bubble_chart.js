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
  var width = 1024;
  var height = 750;

  // tooltip for mouseover functionality
  var tooltip = floatingTooltip('impact_tooltip', 240);

  var margin = {top: 20, right: 0, bottom: 0, left: 0};

  // Locations to move bubbles towards, depending
  // on which view mode is selected.
  var center = { x: width / 2, y: height / 2 };

  // @v4 strength to apply to the position forces
  var forceStrength = 0.03;

  var maxImpactValue = 500;

  var maxCircleRadius = 50;

  var radiusScale = d3.scaleSqrt();

  var colorScale = d3.scaleSequential()
    .interpolator(d3.interpolatePuBuGn);

  var year = { start: "1950", end: "2000"};

  var centersX = [width / 4, width / 2, width - (width / 4)];
  var centersY = [height / 4, height / 2, height - (height/ 4)];

  var fieldCenters = [
    { x: centersX[0], y: centersY[0] },
    { x: centersX[1], y: centersY[0] },
    { x: centersX[2], y: centersY[0] },
    { x: centersX[0], y: centersY[1] },
    { x: centersX[1], y: centersY[1] },
    { x: centersX[2], y: centersY[1] },
    { x: centersX[0], y: centersY[2] },
    { x: centersX[1], y: centersY[2] },
    { x: centersX[2], y: centersY[2] }
  ];

  // X locations of the year titles.
  var fieldsTitles = [
    { field: "Relational Database", x: centersX[0] - 50, y: centersY[0] - 140 },
    { field: "Data Model", x: centersX[1], y: centersY[0] - 140 },
    { field: "Social Network", x: centersX[2] + 50, y: centersY[0] - 140 },
    { field: "Statistics", x: centersX[0] - 50, y: centersY[1] - 90 },
    { field: "Quantitative Finance", x: centersX[1], y: centersY[1] - 90 },
    { field: "Microbiology", x: centersX[2] + 50, y: centersY[1] - 90 },
    { field: "Mathematical Physics", x: centersX[0] - 50, y: centersY[2] - 30 },
    { field: "Computer Science", x: centersX[1], y: centersY[2] - 30 },
    { field: "Quantum Physics", x: centersX[2] + 50, y: centersY[2] - 30 }
  ];

  var rValue = function (d) {
    return d.radius;
  };

  var idValue = function (d) {
    return d.title;
  };

  var textValue = function (d) {
    return d.title.substring(0, 10);
  };

  var numValue = function (d) {
    return +d.value > 0 ? (+d.value).toFixed(2) : 0;
  };

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
    return -Math.pow(rValue(d), 2.0) * forceStrength;
  }

  // Here we create a force layout and
  // @v4 We create a force simulation now and
  //  add forces to it.
  var simulation = d3.forceSimulation()
    // .velocityDecay(0.2)
    .force('x', d3.forceX().strength(forceStrength).x(center.x))
    .force('y', d3.forceY().strength(forceStrength).y(center.y))
    .force('charge', d3.forceManyBody().strength(-2))
    // .force('charge', d3.forceManyBody().strength(function (d) { return charge(d); }))
    .on('tick', ticked);


  // @v4 Force starts up automatically,
  //  which we don't want as there aren't any nodes yet.
  simulation.stop();

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
    radiusScale.domain([0, maxImpactValue])
      .range([0, maxCircleRadius]);

    // colorScale.domain([0, maxImpactValue]);

    // convert raw data into nodes data
    nodes = createNodes(rawData);

    // Create a SVG element inside the provided selector
    // with desired size.
    svg = d3.select(selector)
      .append('svg')
      .attr('width', width)
      .attr('height', height);

    var bubbleGroup = svg.append('g');

    // Bind nodes data to what will become DOM elements to represent them.
    bubbles = bubbleGroup.selectAll('.bubble').data(nodes, idValue);

    // Create new circle elements each with class `bubble`.
    // There will be one circle.bubble for each object in the nodes array.
    // Initially, their radius (r attribute) will be 0.
    // @v4 Selections are immutable, so lets capture the
    //  enter selection to apply our transtition to below.

    var maxValue = d3.max(nodes, function(d) { return +d[String(year.start)]; });
    colorScale.domain([0, maxValue]);

    var bubblesE = bubbles.enter().append('circle')
      .classed('bubble', true)
      .attr('r', 0)
      .attr('fill', function (d) { return colorScale(d.value); } )
      .on('mouseover', showDetail)
      .on('mouseout', hideDetail);

    // @v4 Merge the original empty selection and the enter selection
    bubbles = bubbles.merge(bubblesE);


    // Fancy transition to make bubbles appear, ending with the
    // correct radius
    bubbles.transition()
      .duration(2000)
      .attr('r', rValue);

    // label is the container div for all the labels that sit on top of
    // the bubbles
    // - remember that we are keeping the labels in plain html and
    //  the bubbles in svg
    // label = d3.select(selector).selectAll("#bubble-labels")
    //   .data([nodes])
    //   .enter()
    //   .append("div")
    //     .attr("id", "bubble-labels");
    //
    // createLabels();

    // Set the simulation's nodes to our newly created nodes array.
    // @v4 Once we set the nodes, the simulation will start running automatically!
    simulation.nodes(nodes)
      .force('collision', d3.forceCollide().radius(rValue));

    simulation.restart();
  };

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
    var myNodes = rawData.map(function (d) {
      var node = {
        title: idValue(d),
        radius: radiusScale(+d[year.start]),
        value: +d[year.start],
        x: Math.random() * 900,
        y: Math.random() * 800,
        field: Math.floor(Math.random() * 9)
      };

      for (var i = +year.start; i <= +year.end; i++) {
        node[String(i)] = d[String(i)];
      }

      return node;

    });

    return myNodes;
  }

  // ---
  // updateLabels is more involved as we need to deal with getting the sizing
  // to work well with the font size
  // ---
  // function createLabels() {
  //   var maxValue = d3.max(nodes, function(d) { return +d[String(year.start)]; });
  //
  //   // Sizes bubbles based on area.
  //   // @v4: new flattened scale names.
  //   var radiusScale = d3.scaleSqrt()
  //     .range([0, 20])
  //     .domain([0, maxValue]);
  //
  //   // as in updateNodes, we use idValue to define what the unique id for each data
  //   // point is
  //   label = label.selectAll(".bubble-label").data(nodes, idValue);
  //
  //   // labels are anchors with div's inside them
  //   // labelEnter holds our enter selection so it
  //   // is easier to append multiple elements to this selection
  //   label = label.enter().append("a")
  //     .attr("class", "bubble-label")
  //     .attr("href", function (d) {
  //       return "#" + encodeURIComponent(idValue(d));
  //     });
  //
  //   label.append("div")
  //     .attr("class", "bubble-label-name")
  //     .text(textValue);
  //
  //   label.append("div")
  //     .attr("class", "bubble-label-value")
  //     .text(numValue);
  //
  //   // label font size is determined based on the size of the bubble
  //   // this sizing allows for a bit of overhang outside of the bubble
  //   // - remember to add the 'px' at the end as we are dealing with
  //   //  styling divs
  //   label
  //     .style("font-size", "0px")
  //     .style("width", "0px")
  //     // .style("display", function(d) { return numValue(d) === 0 ? "none" : "block"; } );
  //     .style("display", "none"); // TODO move labels to mouseovers
  //
  //   label
  //     .transition().duration(2000)
  //     .style("font-size", function (d) { return Math.max(8, radiusScale(numValue(d) / 2)) + "px"; })
  //     .style("width", function (d) { return (2.5 * radiusScale(numValue(d))) + "px"; });
  //
  //   // interesting hack to get the 'true' text width
  //   // - create a span inside the label
  //   // - add the text to this span
  //   // - use the span to compute the nodes 'dx' value
  //   //  which is how much to adjust the label by when
  //   //  positioning it
  //   // - remove the extra span
  //   label.append("span")
  //     .text(textValue)
  //     .each(function(d) { d.dx = Math.max(2.5 * radiusScale(numValue(d)), this.getBoundingClientRect().width); })
  //     .remove();
  //
  //   // reset the width of the label to the actual width
  //   label.style("width", function (d) { return d.dx + "px"; });
  //
  //   // compute and store each nodes 'dy' value - the
  //   // amount to shift the label down
  //   // 'this' inside of D3's each refers to the actual DOM element
  //   // connected to the data node
  //   return label.each(function(d) { d.dy = this.getBoundingClientRect().height; });
  //
  // }
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
    // return label
    //   .style("left", function (d) { return ((margin.left + d.x) - (d.dx / 2)) + "px"; })
    //   .style("top", function (d) { return ((margin.top + d.y) - (d.dy / 2)) + "px"; });
  }

  /*
   * Provides a x value for each node to be used with the split by year
   * x force.
   */
  function nodeFieldPosX(d) {
    return fieldCenters[d.field].x;
  }

  function nodeFieldPosY(d) {
    return fieldCenters[d.field].y;
  }

  /*
   * Sets visualization in "single group mode".
   * The year labels are hidden and the force layout
   * tick function is set to move all nodes to the
   * center of the visualization.
   */
  function groupBubbles() {
    hideFieldTitles();

    // @v4 Reset the 'x' force to draw the bubbles to the center.
    simulation.force('x', d3.forceX().strength(forceStrength).x(center.x));
    simulation.force('y', d3.forceY().strength(forceStrength).y(center.y));

    // @v4 We can reset the alpha value and restart the simulation
    simulation.alpha(1).restart();
  }


  function splitBubbles() {
    showFieldTitles();

    // @v4 Reset the 'x' force to draw the bubbles to their year centers
    simulation.force('x', d3.forceX().strength(0.05).x(nodeFieldPosX));
    simulation.force('y', d3.forceY().strength(0.05).y(nodeFieldPosY));

    // @v4 We can reset the alpha value and restart the simulation
    simulation.alpha(1).restart();
  }

  function hideFieldTitles() {
    svg.selectAll('.field').remove();
  }

  function showFieldTitles() {
    // Another way to do this would be to create
    // the year texts once and then just hide them.
    var fields = svg.selectAll('.field')
      .data(fieldsTitles);

    // fields.enter().append('circle')
    //   .classed('bubble', true)
    //   .attr('cx', function (d) { return d.x; })
    //   .attr('cy', function (d) { return d.y; })
    //   .attr('r', 5)
    //   .attr('fill', "red");

    fields.enter().append('text')
      .attr('class', 'field')
      .attr('x', function (d) { return d.x; })
      .attr('y', function (d) { return d.y; })
      .attr('text-anchor', 'middle')
      .text(function (d) { return d.field; });
  }

  /*
   * Function called on mouseover to display the
   * details of a bubble in the tooltip.
   */
  function showDetail(d) {
    // change outline to indicate hover state.
    d3.select(this).attr('stroke', 'black');

    var content = '<span class="name">Title: </span><span class="value">' +
                  idValue(d) +
                  '</span><br/>' +
                  '<span class="name">Impact Value: </span><span class="value">' +
                  numValue(d) +
                  '</span>';

    tooltip.showTooltip(content, d3.event);
  }

  /*
   * Hides tooltip
   */
  function hideDetail(d) {
    // reset outline
    d3.select(this)
      .attr('stroke', 'white');

    tooltip.hideTooltip();
  }
/*
   * Externally accessible function (this is attached to the
   * returned chart function). Allows the visualization to toggle
   * between "single group" and "split by year" modes.
   *
   * displayName is expected to be a string and either 'year' or 'all'.
   */
  chart.toggleDisplay = function (displayName) {
    if (displayName === 'field') {
      splitBubbles();
    } else {
      groupBubbles();
    }
  };

  // when the input range changes update the circle
  d3.select("#nRadius").on("input", function() {
    updateSlider(+this.value);
  });
// when the input range changes update the circle
  d3.select("#nRadius").on("change", function() {
    update(+this.value);
  });

  function updateSlider(year) {
    // adjust the text on the range slider
    d3.select("#nRadius-value").text(year);
    d3.select("#nRadius").property("value", year);
  }

  function update(year) {
    // var maxValue = d3.max(nodes, function(d) { return +d[String(year)]; });

    // Sizes bubbles based on area.
    // @v4: new flattened scale names.
    // var radiusScale = d3.ScaleLog()
    //   .range([0, 20])
    //   .domain([0, maxImpactValue]);

    // update node sizes
    nodes.forEach(function (d) {
      d.radius = radiusScale(+d[String(year)]);
      d.value = +d[String(year)];
    });

    simulation.force('collision', d3.forceCollide().radius(rValue));

    var strength = simulation.force('collision').strength();

    // turn off collision
    // simulation.force('collision').strength(strength * 0.1);
    var maxValue = d3.max(nodes, function(d) { return +d[String(year)]; });
    colorScale.domain([0, maxValue]);

    bubbles.transition()
      .duration(1000)
      .attr('fill', function (d) { return colorScale(d.value); } )
      .attr('r', rValue);

    // updateLabels(year);

    // slowly bring collision force back up
    // endTime = 1000;
    // transitionTimer = d3.timer(function(elapsed) {
    //   var dt = elapsed / endTime;
    //   simulation.force('collision').strength(Math.pow(dt, 3) * strength);
    //   if (dt >= 1.0) transitionTimer.stop();
    // });

    simulation.alphaTarget(0.2).restart();
  }

  // function updateLabels(year) {
  //   var maxValue = d3.max(nodes, function(d) { return +d[String(year)]; });
  //
  //   // Sizes bubbles based on area.
  //   // @v4: new flattened scale names.
  //   var radiusScale = d3.scaleSqrt()
  //     .range([0, 20])
  //     .domain([0, maxValue]);
  //
  //   d3.select("#viz").selectAll(".bubble-label-value")
  //     .text(numValue);
  //
  //   var label = d3.select("#viz").selectAll(".bubble-label");
  //
  //   label
  //     // .style("display", function(d) { return numValue(d) === 0 ? "none" : "block"; });
  //     .style("display", "none"); // TODO move labels to mouseovers
  //
  //   label
  //     .transition().duration(1000)
  //     .style("font-size", function (d) { return Math.max(8, radiusScale(numValue(d) / 2)) + "px"; })
  //     .style("width", function (d) { return (2.5 * radiusScale(numValue(d))) + "px"; });
  //
  //   label.append("span")
  //     .text(idValue)
  //     .each(function(d) { d.dx = Math.max(2.5 * radiusScale(numValue(d)), this.getBoundingClientRect().width); })
  //     .remove();
  //
  //   label
  //     .style("width", function (d) { return d.dx + "px"; });
  //
  //   return label.each(function(d) { d.dy = this.getBoundingClientRect().height; });
  // }
  //


  chart.year = function(value) {
    if (!arguments.length) {
      return year;
    } else {
      year = value;
      return chart;
    }
  };

  chart.maxImpactValue = function(value) {
    if (!arguments.length) {
      return maxImpactValue;
    } else {
      maxImpactValue = value;
      return chart;
    }
  };

  chart.maxCircleRadius = function(value) {
    if (!arguments.length) {
      return maxCircleRadius;
    } else {
      maxCircleRadius= value;
      return chart;
    }
  };


  // return the chart function from closure.
  return chart;
}

/*
 * Sets up the layout buttons to allow for toggling between view modes.
 */
function setupButtons() {
  d3.select('#toolbar')
    .selectAll('.button')
    .on('click', function () {
      // Remove active class from all buttons
      d3.selectAll('.button').classed('active', false);
      // Find the button just clicked
      var button = d3.select(this);

      // Set it as the active button
      button.classed('active', true);

      // Get the id of the button
      var buttonId = button.attr('id');

      // Toggle the bubble chart based on
      // the currently clicked button.
      myBubbleChart.toggleDisplay(buttonId);
    });
}
