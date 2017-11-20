/**
 * Copyright (C) 2014-2017 Xavier Witdouck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zavtech.morpheus.beakerx;


import java.awt.*;
import java.text.Format;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jupyter.Displayer;
import jupyter.Displayers;

import com.twosigma.beakerx.jvm.object.OutputCell;
import com.twosigma.beakerx.table.TableDisplay;

import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.util.Collect;
import com.zavtech.morpheus.util.IO;
import com.zavtech.morpheus.util.text.SmartFormat;
import com.zavtech.morpheus.viz.chart.Chart;
import com.zavtech.morpheus.viz.chart.xy.XyPlot;
import com.zavtech.morpheus.viz.html.HtmlCode;
import com.zavtech.morpheus.viz.js.JsCode;

/**
 * A class that registers Morpheus DataFrame displayers for Jupyter Notebook using Two Sigma BeakerX
 *
 * @author Xavier Witdouck
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 */
public class DataFrameDisplayer {


    public static void registerAll() {
        registerTableDisplay();
        registerChartDisplay();
    }

    /**
     * Registers a Jupyter Displayer to display a Morpheus DataFrame using BeakerX fancy tables.
     */
    @SuppressWarnings("unchecked")
    public static void registerTableDisplay() {
        try {
            Class<DataFrame> type = (Class<DataFrame>)Class.forName("com.zavtech.morpheus.reference.XDataFrame");
            Displayers.register(type, new Displayer<DataFrame>() {
                @Override
                @SuppressWarnings("unchecked")
                public Map<String,String> display(DataFrame frame) {
                    final Format format = new SmartFormat();
                    final Stream<String> stream = frame.cols().keys().map(Object::toString);
                    final List<String> columns = stream.collect(Collectors.toList());
                    final TableDisplay display = new TableDisplay(
                        frame.rowCount(),
                        frame.colCount(),
                        columns,
                        (colIndex, rowIndex) -> {
                            final Object value = frame.data().getValue(rowIndex, colIndex);
                            return value == null ? null : format.format(value);
                        }
                    );
                    display.display();
                    return OutputCell.DISPLAYER_HIDDEN;
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    @SuppressWarnings("unchecked")
    public static void registerChartDisplay() {
        try {
            register((Class<Chart>)Class.forName("com.zavtech.morpheus.viz.google.GChart"));
            register((Class<Chart>)Class.forName("com.zavtech.morpheus.viz.jfree.JFCatChart"));
            register((Class<Chart>)Class.forName("com.zavtech.morpheus.viz.jfree.JFXyChart"));
            register((Class<Chart>)Class.forName("com.zavtech.morpheus.viz.jfree.JFPieChart"));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }


    /**
     * Registers a Displayer for a specific Chart data type (as JVM-REPR does not seem to support interfaces for now).
     * @param chartType     the chart class to register
     * @param <C>           the chart type
     */
    private static <C extends Chart> void register(Class<C> chartType) {
        Displayers.register(chartType, new Displayer<C>() {
            @Override
            public Map<String,String> display(C chart) {
                return Collect.asMap(map -> {
                    map.put("text/html", toHtml(chart));
                });
            }
        });
    }


    /**
     * Returns the HTML to render the Chart object in Jupyter Notebook
     * @param chart     the chart reference
     * @return          the resulting HTML
     */
    private static String toHtml(Chart chart) {
        final String chartId = UUID.randomUUID().toString().replace("-", "");
        final String javascript = toJavascript(chartId, chart);
        return HtmlCode.createHtml(html -> {
            html.newElement("script", script -> {
                script.newAttribute("type", "text/javascript");
                script.text(javascript);
            });
            html.newElement("div", div -> {
                final Optional<Dimension> size = chart.options().getPreferredSize();
                final int width = size.map(Dimension::getWidth).orElse(800d).intValue();
                final int height = size.map(Dimension::getHeight).orElse(600d).intValue();
                div.newAttribute("id", String.format("chart_%s", chartId));
                div.newAttribute("style", String.format("float:left;width:%spx;height:%spx;", width, height));
            });
        });
    }


    /**
     * Returns the Javascript that renders either a Google chart or a JFreechart as an embedded image
     * @param chartId       the unique id for the chart div
     * @param chart         the chart object
     * @return              the resulting Javascript
     */
    private static String toJavascript(String chartId, Chart chart) {
        if (chart.getClass().getSimpleName().equals("GChart")) {
            return JsCode.create(jsCode -> {
                jsCode.newLine().write("google.charts.load('current', {'packages':['corechart']});");
                jsCode.newLine().write("google.charts.setOnLoadCallback(drawChart_%s);", chartId);
                jsCode.newLine();
                final String functionName = String.format("drawChart_%s", chartId);
                final String divId = chart.options().getId().orElse(String.format("chart_%s", chartId));
                jsCode.newLine().newLine();
                chart.accept(jsCode, functionName, divId);
            });
        } else {
            return JsCode.create(jsCode -> {
                jsCode.write("console.info('Writing charts...');");
                jsCode.newLine();
                jsCode.write("drawChart_%s();", chartId);
                final String functionName = String.format("drawChart_%s", chartId);
                final String divId = chart.options().getId().orElse(String.format("chart_%s", chartId));
                jsCode.newLine().newLine();
                chart.accept(jsCode, functionName, divId);
            });
        }
    }



    public static void main(String[] args) {
        DataFrame<Integer,String> data = DataFrame.read().csv(options -> {
            options.setResource("http://zavtech.com/data/samples/cars93.csv");
            options.setExcludeColumnIndexes(0);
        });

        String regressand = "Horsepower";
        String regressor = "EngineSize";
        DataFrame<Integer,String> xy = data.cols().select(regressand, regressor);
        Chart<XyPlot<Double>> result = Chart.create().withScatterPlot(xy, false, regressor, chart -> {
            chart.title().withText(regressand + " regressed on " + regressor);
            chart.subtitle().withText("Single Variable Linear Regression");
            chart.plot().style(regressand).withColor(java.awt.Color.RED).withPointsVisible(true);
            chart.plot().trend(regressand).withColor(java.awt.Color.BLACK);
            chart.plot().axes().domain().label().withText(regressor);
            chart.plot().axes().domain().format().withPattern("0.00;-0.00");
            chart.plot().axes().range(0).label().withText(regressand);
            chart.plot().axes().range(0).format().withPattern("0;-0");
            chart.legend().on().bottom();
            chart.options().withPreferredSize(800, 600);
        });

        final String html = toHtml(result);
        IO.println(html);
    }

}
