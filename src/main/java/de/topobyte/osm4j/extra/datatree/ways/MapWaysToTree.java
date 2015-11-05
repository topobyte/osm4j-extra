// Copyright 2015 Sebastian Kuerten
//
// This file is part of osm4j.
//
// osm4j is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// osm4j is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with osm4j. If not, see <http://www.gnu.org/licenses/>.

package de.topobyte.osm4j.extra.datatree.ways;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Envelope;

import de.topobyte.osm4j.core.access.OsmIterator;
import de.topobyte.osm4j.core.access.OsmOutputStream;
import de.topobyte.osm4j.core.model.iface.EntityContainer;
import de.topobyte.osm4j.core.model.iface.EntityType;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.impl.Bounds;
import de.topobyte.osm4j.extra.datatree.ClosingFileOutputStream;
import de.topobyte.osm4j.extra.datatree.ClosingFileOutputStreamFactory;
import de.topobyte.osm4j.extra.datatree.ClosingFileOutputStreamPool;
import de.topobyte.osm4j.extra.datatree.DataTree;
import de.topobyte.osm4j.extra.datatree.DataTreeOpener;
import de.topobyte.osm4j.extra.datatree.Node;
import de.topobyte.osm4j.extra.nodearray.NodeArray;
import de.topobyte.osm4j.extra.nodearray.NodeArrayInteger;
import de.topobyte.osm4j.extra.progress.NodeProgress;
import de.topobyte.osm4j.utils.AbstractTaskSingleInputFile;
import de.topobyte.osm4j.utils.FileFormat;
import de.topobyte.osm4j.utils.OsmIoUtils;
import de.topobyte.osm4j.utils.config.PbfConfig;
import de.topobyte.osm4j.utils.config.PbfOptions;
import de.topobyte.osm4j.utils.config.TboConfig;
import de.topobyte.osm4j.utils.config.TboOptions;
import de.topobyte.utilities.apache.commons.cli.OptionHelper;

public class MapWaysToTree extends AbstractTaskSingleInputFile
{

	private static final String OPTION_FILE_NAMES = "filenames";
	private static final String OPTION_OUTPUT_FORMAT = "output_format";
	private static final String OPTION_TREE = "tree";
	private static final String OPTION_NODE_ARRAY = "node_array";

	@Override
	protected String getHelpMessage()
	{
		return MapWaysToTree.class.getSimpleName() + " [options]";
	}

	public static void main(String[] args) throws IOException
	{
		MapWaysToTree task = new MapWaysToTree();

		task.setup(args);

		task.execute();
	}

	private String pathTree;
	private String pathNodeArray;

	private String fileNames;
	private FileFormat outputFormat;
	private PbfConfig pbfConfig;
	private TboConfig tboConfig;

	private boolean writeMetadata = true;

	public MapWaysToTree()
	{
		// @formatter:off
		OptionHelper.add(options, OPTION_FILE_NAMES, true, true, "names of the data files to create");
		OptionHelper.add(options, OPTION_OUTPUT_FORMAT, true, true, "the file format of the output");
		OptionHelper.add(options, OPTION_TREE, true, true, "directory to store output in");
		OptionHelper.add(options, OPTION_NODE_ARRAY, true, true, "a path to a node array");
		PbfOptions.add(options);
		TboOptions.add(options);
		// @formatter:on
	}

	@Override
	protected void setup(String[] args)
	{
		super.setup(args);

		String outputFormatName = line.getOptionValue(OPTION_OUTPUT_FORMAT);
		outputFormat = FileFormat.parseFileFormat(outputFormatName);
		if (outputFormat == null) {
			System.out.println("invalid output format");
			System.out.println("please specify one of: "
					+ FileFormat.getHumanReadableListOfSupportedFormats());
			System.exit(1);
		}

		pbfConfig = PbfOptions.parse(line);
		tboConfig = TboOptions.parse(line);

		fileNames = line.getOptionValue(OPTION_FILE_NAMES);

		pathTree = line.getOptionValue(OPTION_TREE);
		pathNodeArray = line.getOptionValue(OPTION_NODE_ARRAY);
	}

	public void execute() throws IOException
	{
		/*
		 * Tree, node array and way iterator
		 */

		Path dirTree = Paths.get(pathTree);

		NodeArray array = new NodeArrayInteger(new File(pathNodeArray), 1024,
				4096);
		DataTree tree = DataTreeOpener.open(dirTree.toFile());

		InputStream fis = new FileInputStream(getInputFile());
		InputStream bis = new BufferedInputStream(fis);
		OsmIterator iterator = OsmIoUtils.setupOsmIterator(bis, inputFormat,
				writeMetadata);

		// This is where we write ways to that do not contain any reference
		// within the world bounds

		Path pathNonMatched = dirTree.resolve("non-matched-ways.tbo");
		OutputStream osNone = new FileOutputStream(pathNonMatched.toFile());
		OutputStream bosNone = new BufferedOutputStream(osNone);
		OsmOutputStream osmOutputNone = OsmIoUtils.setupOsmOutput(osNone,
				outputFormat, writeMetadata, pbfConfig, tboConfig);
		Output outputNone = new Output(pathNonMatched, bosNone, osmOutputNone);

		// Set up outputs

		ClosingFileOutputStreamFactory outputStreamFactory = new ClosingFileOutputStreamPool();
		int idFactory = 0;

		Map<Node, Output> outputs = new HashMap<>();

		for (Node leaf : tree.getLeafs()) {
			String dirname = Long.toHexString(leaf.getPath());
			Path dir = dirTree.resolve(dirname);
			Path file = dir.resolve(fileNames);

			ClosingFileOutputStream os = new ClosingFileOutputStream(
					outputStreamFactory, file.toFile(), idFactory++);
			OutputStream bos = new BufferedOutputStream(os);
			OsmOutputStream osmOutput = OsmIoUtils.setupOsmOutput(os,
					outputFormat, writeMetadata, pbfConfig, tboConfig);
			Output output = new Output(file, bos, osmOutput);
			outputs.put(leaf, output);

			Envelope box = leaf.getEnvelope();
			osmOutput.write(new Bounds(box.getMinX(), box.getMaxX(), box
					.getMaxY(), box.getMinY()));
		}

		// Process ways

		int nNone = 0;
		int nMultiple = 0;

		NodeProgress progress = new NodeProgress();
		progress.printTimed(1000);

		while (iterator.hasNext()) {
			EntityContainer container = iterator.next();
			if (container.getType() != EntityType.Way) {
				continue;
			}
			OsmWay way = (OsmWay) container.getEntity();

			if (way.getNumberOfNodes() == 0) {
				continue;
			}

			progress.increment();

			List<Node> leafs = null;
			for (int i = 0; i < way.getNumberOfNodes(); i++) {
				long nodeId = way.getNodeId(i);
				OsmNode node = array.get(nodeId);
				leafs = tree.query(node.getLongitude(), node.getLatitude());
				if (!leafs.isEmpty()) {
					break;
				}
			}

			if (leafs.size() == 0) {
				outputNone.getOsmOutput().write(way);
				nNone++;
			}

			if (leafs.size() > 1) {
				nMultiple++;
			}

			for (Node leaf : leafs) {
				Output output = outputs.get(leaf);
				output.getOsmOutput().write(way);
			}
		}

		progress.stop();

		System.out.println("none: " + nNone);
		System.out.println("multiple: " + nMultiple);

		bis.close();
		array.close();

		outputNone.getOsmOutput().complete();
		outputNone.getOutputStream().close();

		for (Output output : outputs.values()) {
			output.getOsmOutput().complete();
			output.getOutputStream().close();
		}
	}

}