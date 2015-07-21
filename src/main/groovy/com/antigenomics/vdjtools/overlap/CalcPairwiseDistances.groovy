/*
 * Copyright 2013-2015 Mikhail Shugay (mikhail.shugay@gmail.com)
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

package com.antigenomics.vdjtools.overlap

import com.antigenomics.vdjtools.Software
import com.antigenomics.vdjtools.basic.SegmentUsage
import com.antigenomics.vdjtools.sample.SampleCollection

import static com.antigenomics.vdjtools.util.ExecUtil.formOutputPath
import static com.antigenomics.vdjtools.util.ExecUtil.toPlotPath
import static com.antigenomics.vdjtools.util.RUtil.execute

def I_TYPE_DEFAULT = "aa"
def cli = new CliBuilder(usage: "CalcPairwiseDistances [options] " +
        "[sample1 sample2 sample3 ... if not -m] output_prefix")
cli.h("display help message")
cli.m(longOpt: "metadata", argName: "filename", args: 1,
        "Metadata file. First and second columns should contain file name and sample id. " +
                "Header is mandatory and will be used to assign column names for metadata.")
cli._(longOpt: "low-mem", "Will process all sample pairs sequentially, avoiding" +
        " loading all of them into memory. Slower but memory-efficient mode.")
cli.i(longOpt: "intersect-type", argName: "string", args: 1,
        "Intersection rule to apply. Allowed values: $OverlapType.allowedNames. " +
                "Will use '$I_TYPE_DEFAULT' by default.")
cli._(longOpt: "plot-type", argName: "pdf|png", args: 1, "Plot output format [default=pdf]")
cli.p(longOpt: "plot", "[plotting] Turns plotting on.")

def opt = cli.parse(args)

if (opt == null) {
    //cli.usage()
    System.exit(-1)
}

if (opt.h) {
    cli.usage()
    System.exit(0)
}

// Check if metadata is provided

def metadataFileName = opt.m

if (metadataFileName ? opt.arguments().size() != 1 : opt.arguments().size() < 4) {
    if (metadataFileName)
        println "[ERROR] Only output prefix should be provided in case of -m"
    else
        println "[ERROR] At least 3 sample file names should be provided if not using -m"
    cli.usage()
    System.exit(-1)
}

def outputPrefix = opt.arguments()[-1],
    lowMem = (boolean) opt.'low-mem',
    plot = (boolean) opt.p,
    plotType = (opt.'plot-type' ?: "pdf").toString()

def scriptName = getClass().canonicalName.split("\\.")[-1]

// Select overlap type

def iName = opt.i ?: I_TYPE_DEFAULT
def intersectionType = OverlapType.getByShortName(iName)

if (!intersectionType) {
    println "[ERROR] Bad overlap type specified ($iName). " +
            "Allowed values are: $OverlapType.allowedNames"
    System.exit(-1)
}

//
// Batch load all samples
//

println "[${new Date()} $scriptName] Reading samples"

boolean store, lazy
(store, lazy) = lowMem ? [false, true] : [true, false]

def sampleCollection = metadataFileName ?
        new SampleCollection((String) metadataFileName, Software.VDJtools, store, lazy) :
        new SampleCollection(opt.arguments()[0..-2], Software.VDJtools, store, lazy)

if (sampleCollection.size() < 3) {
    println "[ERROR] Metadata file should contain at least 3 samples"
    System.exit(-1)
}

println "[${new Date()} $scriptName] ${sampleCollection.size()} samples loaded"

//
// Perform overlap for all specified overlap types
//

println "[${new Date()} $scriptName] Intersecting by $intersectionType"

SegmentUsage.VERBOSE = false
Overlap.VERBOSE = false
OverlapEvaluator.VERBOSE = false

def pairedIntersectionBatch = new PairwiseOverlap(sampleCollection, intersectionType)

println "[${new Date()} $scriptName] Writing results"

def outputFileName = formOutputPath(outputPrefix, "intersect", "batch", intersectionType.shortName)

new File(outputFileName).withPrintWriter { pw ->
    pw.println(pairedIntersectionBatch.header)
    pw.println(pairedIntersectionBatch.toString())
}

if (plot) {
    println "[${new Date()} $scriptName] Plotting"
    execute("pairwise_distance_plot.r", outputFileName, toPlotPath(outputFileName, plotType))
}

println "[${new Date()} $scriptName] Finished"