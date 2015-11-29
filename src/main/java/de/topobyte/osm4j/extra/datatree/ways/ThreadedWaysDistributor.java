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

import gnu.trove.map.TLongObjectMap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import de.topobyte.osm4j.core.access.OsmStreamOutput;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.extra.datatree.Node;
import de.topobyte.osm4j.extra.threading.ObjectBuffer;
import de.topobyte.osm4j.extra.threading.write.NodeWriteRequest;
import de.topobyte.osm4j.extra.threading.write.WayWriteRequest;
import de.topobyte.osm4j.extra.threading.write.WriteRequest;
import de.topobyte.osm4j.extra.threading.write.WriterRunner;
import de.topobyte.osm4j.utils.FileFormat;
import de.topobyte.osm4j.utils.OsmOutputConfig;
import de.topobyte.osm4j.utils.buffer.ParallelExecutor;

public class ThreadedWaysDistributor extends AbstractWaysDistributor
{

	public ThreadedWaysDistributor(Path pathTree, String fileNamesNodes1,
			String fileNamesNodes2, String fileNamesWays,
			String fileNamesOutputWays, String fileNamesOutputNodes,
			FileFormat inputFormatNodes, FileFormat inputFormatWays,
			OsmOutputConfig outputConfig)
	{
		super(pathTree, fileNamesNodes1, fileNamesNodes2, fileNamesWays,
				fileNamesOutputWays, fileNamesOutputNodes, inputFormatNodes,
				inputFormatWays, outputConfig);
	}

	private ObjectBuffer<WriteRequest> buffer = new ObjectBuffer<>(10000, 100);

	@Override
	public void execute() throws IOException
	{
		prepare();

		Runnable distributor = new Runnable() {

			@Override
			public void run()
			{
				try {
					distribute();
					buffer.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
		WriterRunner writer = new WriterRunner(buffer);

		List<Runnable> tasks = new ArrayList<>();
		tasks.add(distributor);
		tasks.add(writer);

		ParallelExecutor executor = new ParallelExecutor(tasks);
		executor.execute();

		finish();
	}

	@Override
	protected void write(Node leaf, OsmWay way, TLongObjectMap<OsmNode> nodes)
			throws IOException
	{
		OsmStreamOutput wayOutput = outputsWays.get(leaf);
		OsmStreamOutput nodeOutput = outputsNodes.get(leaf);

		buffer.write(new WayWriteRequest(way, wayOutput.getOsmOutput()));
		for (OsmNode node : nodes.valueCollection()) {
			buffer.write(new NodeWriteRequest(node, nodeOutput.getOsmOutput()));
		}
	}

}