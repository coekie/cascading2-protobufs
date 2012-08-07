package com.squareup.cascading2.function;

import cascading.flow.hadoop.HadoopFlowConnector;
import cascading.flow.hadoop.HadoopFlowProcess;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.pipe.Each;
import cascading.pipe.Pipe;
import cascading.scheme.hadoop.TextLine;
import cascading.tap.hadoop.Hfs;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import cascading.tuple.TupleEntryCollector;
import cascading.tuple.TupleEntryIterator;
import com.squareup.cascading2.generated.Example;
import com.squareup.cascading2.scheme.ProtobufScheme;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Created with IntelliJ IDEA. User: duxbury Date: 8/6/12 Time: 11:58 AM To change this template use
 * File | Settings | File Templates.
 */
public class TestExpandProto extends TestCase {

  private static final Example.Person.Builder BRYAN =
      Example.Person.newBuilder().setName("bryan").setId(1).setEmail("bryan@mail.com");
  private static final Example.Person.Builder LUCAS =
      Example.Person.newBuilder().setName("lucas").setId(2);

  public void testSimple() throws Exception {
    ExpandProto allFields = new ExpandProto(Example.Person.class);
    List<Tuple> results =
        operateFunction(allFields, new TupleEntry(new Fields("value"), new Tuple(BRYAN.build())));
    Tuple result = results.get(0);
    assertEquals(new Tuple(1, "bryan", "bryan@mail.com"), result);

    results =
        operateFunction(allFields, new TupleEntry(new Fields("value"), new Tuple(LUCAS.build())));
    result = results.get(0);
    assertEquals(new Tuple(2, "lucas", null), result);
  }

  public void testNested() throws Exception {
    ExpandProto allFields = new ExpandProto(Example.Partnership.class);
    List<Tuple> results = operateFunction(allFields, new TupleEntry(new Fields("value"), new Tuple(
        Example.Partnership
            .newBuilder()
            .setLeader(BRYAN)
            .setFollower(LUCAS)
            .build())));
    assertEquals(new Tuple(BRYAN.build(), LUCAS.build()), results.get(0));

    results = operateFunction(new ExpandProto(Example.Partnership.class, "leader"),
        new TupleEntry(new Fields("value"), new Tuple(Example.Partnership
            .newBuilder()
            .setLeader(BRYAN)
            .setFollower(LUCAS)
            .build())));
    assertEquals(new Tuple(BRYAN.build()), results.get(0));
  }

  public void testConstructorErrorCases() throws Exception {
    fail();
  }

  public void testInFlow() throws Exception {
    FileSystem.get(new Configuration()).delete(new Path("/tmp/input"), true);
    FileSystem.get(new Configuration()).delete(new Path("/tmp/output"), true);

    Hfs inTap = new Hfs(new ProtobufScheme("value", Example.Person.class), "/tmp/input");
    TupleEntryCollector collector = inTap.openForWrite(new HadoopFlowProcess());
    collector.add(new TupleEntry(new Fields("value"), new Tuple(BRYAN.build())));
    collector.close();

    Pipe inPipe = new Pipe("in");
    Pipe p = new Each(inPipe, new Fields("value"), new ExpandProto(Example.Person.class), new Fields("id", "name", "email"));

    Hfs sink = new Hfs(new TextLine(), "/tmp/output");
    new HadoopFlowConnector().connect(inTap, sink, p).complete();

    TupleEntryIterator iter = sink.openForRead(new HadoopFlowProcess());
    List<Tuple> results = new ArrayList<Tuple>();
    while (iter.hasNext()) {
      results.add(iter.next().getTupleCopy());
    }
    assertEquals(1, results.size());

    assertEquals(new Tuple(0, 1, "bryan", "bryan@mail.com").toString(), results.get(0).toString());
  }

  private static List<Tuple> operateFunction(Function func, final TupleEntry argument) {
    final List<Tuple> results = new ArrayList<Tuple>();
    func.operate(null, new FunctionCall() {
      @Override public TupleEntry getArguments() {
        return argument;
      }

      @Override public TupleEntryCollector getOutputCollector() {
        return new TupleEntryCollector() {
          @Override protected void collect(TupleEntry tupleEntry) throws IOException {
            results.add(tupleEntry.getTuple());
          }
        };
      }

      @Override public Object getContext() {
        throw new UnsupportedOperationException();
      }

      @Override public void setContext(Object o) {
        throw new UnsupportedOperationException();
      }

      @Override public Fields getArgumentFields() {
        return argument.getFields();
      }
    });

    return results;
  }
}
