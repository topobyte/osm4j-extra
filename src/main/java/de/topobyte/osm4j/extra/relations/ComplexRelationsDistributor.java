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

package de.topobyte.osm4j.extra.relations;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.TLongSet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.vividsolutions.jts.geom.Envelope;

import de.topobyte.osm4j.core.access.OsmBridge;
import de.topobyte.osm4j.core.access.OsmInputException;
import de.topobyte.osm4j.core.access.OsmIterator;
import de.topobyte.osm4j.core.access.OsmOutputStream;
import de.topobyte.osm4j.core.access.OsmOutputStreamStreamOutput;
import de.topobyte.osm4j.core.access.OsmStreamOutput;
import de.topobyte.osm4j.core.dataset.InMemoryListDataSet;
import de.topobyte.osm4j.core.dataset.InMemoryMapDataSet;
import de.topobyte.osm4j.core.dataset.ListDataSetLoader;
import de.topobyte.osm4j.core.dataset.ListDataSetReader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmRelation;
import de.topobyte.osm4j.core.resolve.CompositeOsmEntityProvider;
import de.topobyte.osm4j.core.resolve.EntityNotFoundException;
import de.topobyte.osm4j.core.resolve.OsmEntityProvider;
import de.topobyte.osm4j.extra.datatree.Node;
import de.topobyte.osm4j.extra.idbboxlist.IdBboxEntry;
import de.topobyte.osm4j.utils.FileFormat;
import de.topobyte.osm4j.utils.OsmFileInput;
import de.topobyte.osm4j.utils.OsmIoUtils;
import de.topobyte.osm4j.utils.OsmOutputConfig;
import de.topobyte.osm4j.utils.StreamUtil;

public class ComplexRelationsDistributor extends RelationsDistributorBase
{

	public ComplexRelationsDistributor(Path pathTree, Path pathData,
			Path pathOutputEmpty, Path pathOutputNonTree,
			Path pathOutputBboxes, String fileNamesRelations,
			String fileNamesWays, String fileNamesNodes,
			String fileNamesTreeRelations, FileFormat inputFormat,
			OsmOutputConfig outputConfig)
	{
		super(pathTree, pathData, pathOutputEmpty, pathOutputNonTree,
				pathOutputBboxes, fileNamesRelations, fileNamesWays,
				fileNamesNodes, fileNamesTreeRelations, inputFormat,
				outputConfig);
	}

	public void execute() throws IOException, OsmInputException
	{
		init();
		run();
		finish();
		sortFiles();
	}

	@Override
	protected void build(Path path) throws IOException
	{
		Path pathRelations = path.resolve(fileNamesRelations);
		Path pathWays = path.resolve(fileNamesWays);
		Path pathNodes = path.resolve(fileNamesNodes);

		RelationGraph relationGraph = new RelationGraph(false, true);

		InputStream input = StreamUtil.bufferedInputStream(pathRelations
				.toFile());
		OsmIterator iterator = OsmIoUtils.setupOsmIterator(input, inputFormat,
				false);
		relationGraph.build(iterator);
		input.close();

		InMemoryMapDataSet dataRelations = read(pathRelations, true, true);

		System.out.println("Number of relations without relation members: "
				+ relationGraph.getNumNoChildren());
		System.out.println("Number of relations with relation members: "
				+ relationGraph.getIdsHasChildRelations().size());
		System.out.println("Number of child relations: "
				+ relationGraph.getIdsIsChildRelation().size());

		List<Group> groups = relationGraph.buildGroups();
		System.out.println("number of groups: " + groups.size());

		List<RelationGroup> relationGroups = new ArrayList<>();

		for (Group group : groups) {
			try {
				List<OsmRelation> groupRelations = findRelations(
						group.getRelationIds(), dataRelations);
				relationGroups.add(new RelationGroupMultiple(groupRelations));
			} catch (EntityNotFoundException e) {
				System.out.println("unable to build relation group");
			}
		}

		if (relationGroups.size() == 1) {
			InMemoryMapDataSet dataNodes = read(pathNodes, false, false);
			Envelope envelope = box(dataNodes.getNodes().valueCollection());
			List<Node> leafs = tree.query(box(envelope));

			write(relationGroups.get(0), leafs, envelope, dataNodes.getNodes()
					.size());
		} else {
			InMemoryMapDataSet dataNodes = read(pathNodes, false, false);
			InMemoryMapDataSet dataWays = read(pathWays, false, false);

			OsmEntityProvider entityProvider = new CompositeOsmEntityProvider(
					dataNodes, dataWays, dataRelations);

			for (RelationGroup relation : relationGroups) {
				try {
					Set<OsmNode> nodes = relation.findNodes(entityProvider);
					Envelope envelope = box(nodes);
					List<Node> leafs = tree.query(box(envelope));

					write(relation, leafs, envelope, nodes.size());
				} catch (EntityNotFoundException e) {
					//
				}
			}
		}
	}

	private void write(RelationGroup relation, List<Node> leafs,
			Envelope envelope, int size) throws IOException
	{
		if (leafs.size() == 1) {
			nWrittenToTree++;
			write(relation, outputs.get(leafs.get(0)));
		} else {
			nRemaining++;
			write(relation, outputNonTree);
			long id = lowestId(relation.getRelations());
			outputBboxes.write(new IdBboxEntry(id, envelope, size));
		}
	}

	private long lowestId(Collection<OsmRelation> relations)
	{
		long lowest = Long.MAX_VALUE;
		for (OsmRelation relation : relations) {
			lowest = Math.min(lowest, relation.getId());
		}
		return lowest;
	}

	private void write(RelationGroup group, OsmStreamOutput output)
			throws IOException
	{
		Collection<OsmRelation> relations = group.getRelations();
		for (OsmRelation relation : relations) {
			output.getOsmOutput().write(relation);
		}
	}

	private List<OsmRelation> findRelations(TLongSet ids,
			OsmEntityProvider entityProvider) throws EntityNotFoundException
	{
		List<OsmRelation> relations = new ArrayList<>();
		TLongIterator idIterator = ids.iterator();
		while (idIterator.hasNext()) {
			relations.add(entityProvider.getRelation(idIterator.next()));
		}
		return relations;
	}

	private void sortFiles() throws IOException, OsmInputException
	{
		for (Node leaf : tree.getLeafs()) {
			Path path = treeFilesRelations.getPath(leaf);

			OsmFileInput fileInput = new OsmFileInput(path,
					outputConfig.getFileFormat());
			InMemoryListDataSet data = ListDataSetLoader.read(
					fileInput.createIterator(outputConfig.isWriteMetadata()),
					true, true, true);

			data.sort();

			OutputStream output = StreamUtil.bufferedOutputStream(path);
			OsmOutputStream osmOutput = OsmIoUtils.setupOsmOutput(output,
					outputConfig);
			OsmStreamOutput streamOutput = new OsmOutputStreamStreamOutput(
					output, osmOutput);

			OsmBridge.write(new ListDataSetReader(data), streamOutput);
		}
	}

}