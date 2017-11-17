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


import java.text.Format;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jupyter.Displayer;
import jupyter.Displayers;

import com.twosigma.beakerx.jvm.object.OutputCell;
import com.twosigma.beakerx.table.TableDisplay;

import com.zavtech.morpheus.frame.DataFrame;
import com.zavtech.morpheus.util.text.SmartFormat;

/**
 * A class that registers Morpheus DataFrame displayers for Jupyter Notebook using Two Sigma BeakerX
 *
 * @author Xavier Witdouck
 *
 * <p><strong>This is open source software released under the <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></strong></p>
 */
public class DataFrameDisplayer {

    /**
     * Registers a Jupyter Displayer to display a Morpheus DataFrame using BeakerX fancy tables.
     */
    @SuppressWarnings("unchecked")
    public static void register() {
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

}
