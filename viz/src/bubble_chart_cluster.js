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

  // centers of 3x3 grid
  var centers = {
    x: [ width * 0.25, width * 0.5, width * 0.75 ],
    y: [ height * 0.25, height * 0.5, height * 0.75 ]
  };

  // @v4 strength to apply to the position forces
  var forceStrength = 0.03;

  function initCoords(offset) {
    var coords = [];
    for (var y = 0; y < 3; y++) {
      for (var x = 0; x < 3; x++) {
        coords.push({
          x: centers.x[x] + offset.col[x],
          y: centers.y[y] + offset.row[y]
        });
      }
    }
    return coords;
  }

  // initialize coordinates for category cluster centers
  var fieldCenters = initCoords(offset);

  // coordinates of category titles.
  var fieldTitles = initCoords(titleOffset);

  var rValue = function (d) {
    return d.radius;
  };

  var idValue = function (d) {
    return d.title;
  };

  var textValue = function (d) {
    return d.title.substring(0, 10);
  };

  var colorValue = function (d, year) {
    if (year) {
      return +d[String(year) + color.suffix];
    }

    return +d[d.year + color.suffix];
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

    color.scale.domain([minColorValue(nodes, year.start), maxColorValue(nodes, year.start)]);

    var bubblesE = bubbles.enter().append('circle')
      .classed('bubble', true)
      .attr('r', 0)
      .attr('fill', function (d) { return color.scale(d.colorValue); } )
      .on('mouseover', showDetail)
      .on('mouseout', hideDetail);

    // @v4 Merge the original empty selection and the enter selection
    bubbles = bubbles.merge(bubblesE);
    // Fancy transition to make bubbles appear, ending with the
    // correct radius
    bubbles.transition()
      .duration(2000)
      .attr('r', rValue);

    // Set the simulation's nodes to our newly created nodes array.
    // @v4 Once we set the nodes, the simulation will start running automatically!
    simulation.nodes(nodes)
      .force('collision', d3.forceCollide().radius(rValue));

    simulation.restart();
  };

  function maxColorValue(nodes, year) {
    if (color.max) {
      return color.max;
    }
    return d3.max(nodes, function(d) { return +d[String(year) + color.suffix]; });
  }

  function minColorValue(nodes, year) {
    return color.min;
    // return d3.min(nodes, function(d) { return +d[String(year) + color.suffix]; });
  }

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
        impactValue: +d[year.start],
        radius: radiusScale(+d[year.start] > 0 ? +d[year.start] : 0),
        colorValue: colorValue(d, year.start),
        year: year.start,
        x: Math.random() * 900,
        y: Math.random() * 800,
        field: fosIndex(d),
        fieldText: d.primary_field,
        pub: pubIndex(d),
        pubText: d.publisher
      };

      for (var i = +year.start; i <= +year.end; i += +year.step) {
        node[String(i)] = d[String(i)];
        node[String(i) + color.suffix] = d[String(i) + color.suffix]; // TODO
      }

      return node;

    });

    return myNodes;
  }

  function fosIndex(d) {
    var index = fos.indexOf(d.primary_field);
    return index > -1 ? index : 8;
  }

  function pubIndex(d) {
    var index = pubs.indexOf(d.publisher);
    return index > -1 ? index : 8;
  }


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

  function nodePubPosX(d) {
    return fieldCenters[d.pub].x;
  }

  function nodePubPosY(d) {
    return fieldCenters[d.pub].y;
  }

  /*
   * Sets visualization in "single group mode".
   * The group titles are hidden and the force layout
   * tick function is set to move all nodes to the
   * center of the visualization.
   */
  function groupBubbles() {
    // @v4 Reset the 'x' force to draw the bubbles to the center.
    simulation.force('x', d3.forceX().strength(forceStrength).x(center.x));
    simulation.force('y', d3.forceY().strength(forceStrength).y(center.y));

    // @v4 We can reset the alpha value and restart the simulation
    simulation.alpha(1).restart();
  }


  function splitBubbles(category) {
    showTitles(category);

    if (category === 'field') {
      simulation.force('x', d3.forceX().strength(0.03).x(nodeFieldPosX));
      simulation.force('y', d3.forceY().strength(0.03).y(nodeFieldPosY));
    } else {
      simulation.force('x', d3.forceX().strength(0.03).x(nodePubPosX));
      simulation.force('y', d3.forceY().strength(0.03).y(nodePubPosY));
    }

    // @v4 We can reset the alpha value and restart the simulation
    simulation.alpha(1).restart();
  }

  function hideTitles() {
    svg.selectAll('.title').remove();
  }

  function showTitles(category) {
    // Another way to do this would be to create
    // the year texts once and then just hide them.
    var fields = svg.selectAll('.title')
      .data(fieldTitles);

    fields.enter().append('text')
      .attr('class', 'title')
      .attr('x', function (d) { return d.x; })
      .attr('y', function (d) { return d.y; })
      .attr('text-anchor', 'middle')
      .text(function (d, i) { return category === 'field' ? fos[i] : pubs[i]; });
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
                  '<span class="name">Primary Field: </span><span class="value">' +
                  d.fieldText +
                  '</span><br/>' +
                  '<span class="name">Publisher: </span><span class="value">' +
                  d.pubText +
                  '</span><br/>' +
                  '<span class="name">Impact Value: </span><span class="value">' +
                  d.impactValue +
                  '</span><br/>' +
                  '<span class="name">Change in Impact Value: </span><span class="value">' +
                  colorValue(d) +
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
   */
  chart.toggleDisplay = function (displayName) {
    hideTitles();
    if (displayName === 'all') {
      groupBubbles();
    } else {
      splitBubbles(displayName);
    }
  };

// when the input range changes update the slider value
  d3.select("#nRadius").on("input", function() {
    updateSlider(+this.value);
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
      d.radius = radiusScale(+d[year] > 0 ? +d[year] : 0);
      d.impactValue = +d[year];
      d.colorValue = colorValue(d, year);
      d.year = year;
    });

    simulation.force('collision', d3.forceCollide().radius(rValue));

    var strength = simulation.force('collision').strength();

    // turn off collision
    // simulation.force('collision').strength(strength * 0.1);

    color.scale.domain([minColorValue(nodes, year), maxColorValue(nodes, year)]);

    bubbles.transition()
      .duration(1000)
      .attr('fill', function (d) { return color.scale(d.colorValue); } )
      .attr('r', rValue);


    // slowly bring collision force back up
    // endTime = 1000;
    // transitionTimer = d3.timer(function(elapsed) {
    //   var dt = elapsed / endTime;
    //   simulation.force('collision').strength(Math.pow(dt, 3) * strength);
    //   if (dt >= 1.0) transitionTimer.stop();
    // });

    simulation.alphaTarget(0.2).restart();
  }

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
