/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package storm.starter.bolt;

import backtype.storm.Config;
import backtype.storm.generated.GlobalStreamId;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.utils.TimeCacheMap;

import java.util.*;
/**
 * ���ģ����⣺TimeCacheMap
 * @author zhangxw
 *
 */
@SuppressWarnings("all")
public class SingleJoinBolt extends BaseRichBolt {
  OutputCollector _collector;
  Fields _idFields;//��Щfield����ͬ��, ��������join
  Fields _outFields;//�����Ҫ�������
  int _numSources;//����spout
  TimeCacheMap<List<Object>, Map<GlobalStreamId, Tuple>> _pending;
  Map<String, GlobalStreamId> _fieldLocations;//_outFields��spout stream�Ĺ�ϵ, ����gender����genderstream

  public SingleJoinBolt(Fields outFields) {
    _outFields = outFields;
  }

  @Override
  public void prepare(Map conf, TopologyContext context, OutputCollector collector) {
    _fieldLocations = new HashMap<String, GlobalStreamId>();
    _collector = collector;
    int timeout = ((Number) conf.get(Config.TOPOLOGY_MESSAGE_TIMEOUT_SECS)).intValue();
    _pending = new TimeCacheMap<List<Object>, Map<GlobalStreamId, Tuple>>(timeout, new ExpireCallback());
    _numSources = context.getThisSources().size();
    Set<String> idFields = null;
    //�õ�_fieldLocations��_idFields
    for (GlobalStreamId source : context.getThisSources().keySet()) {
      Fields fields = context.getComponentOutputFields(source.get_componentId(), source.get_streamId());
      Set<String> setFields = new HashSet<String>(fields.toList());
      if (idFields == null)
        idFields = setFields;
      else
        idFields.retainAll(setFields);//�õ��������Ͻ��� {1,2}.retainAll({1,3})-->{1}

      for (String outfield : _outFields) {
        for (String sourcefield : fields) {
          if (outfield.equals(sourcefield)) {
            _fieldLocations.put(outfield, source);
          }
        }
      }
    }
    _idFields = new Fields(new ArrayList<String>(idFields));

    if (_fieldLocations.size() != _outFields.size()) {
      throw new RuntimeException("Cannot find all outfields among sources");
    }
  }

  @Override
  public void execute(Tuple tuple) {
    List<Object> id = tuple.select(_idFields);
    GlobalStreamId streamId = new GlobalStreamId(tuple.getSourceComponent(), tuple.getSourceStreamId());
    if (!_pending.containsKey(id)) {
      _pending.put(id, new HashMap<GlobalStreamId, Tuple>());
    }
    Map<GlobalStreamId, Tuple> parts = _pending.get(id);
    if (parts.containsKey(streamId))
      throw new RuntimeException("Received same side of single join twice");
    parts.put(streamId, tuple);
    if (parts.size() == _numSources) {//����������Դ��Сһ�£�����Ϊ�������ݶ����ˣ���ʼjoin
      _pending.remove(id);
      List<Object> joinResult = new ArrayList<Object>();
      for (String outField : _outFields) {
        GlobalStreamId loc = _fieldLocations.get(outField);
        joinResult.add(parts.get(loc).getValueByField(outField));
      }
      _collector.emit(new ArrayList<Tuple>(parts.values()), joinResult);
      System.out.println("������:"+id.get(0)+" -- "+joinResult);//������
      for (Tuple part : parts.values()) {
        _collector.ack(part);
      }
    }
  }

  @Override
  public void declareOutputFields(OutputFieldsDeclarer declarer) {
    declarer.declare(_outFields);
  }

  private class ExpireCallback implements TimeCacheMap.ExpiredCallback<List<Object>, Map<GlobalStreamId, Tuple>> {
    @Override
    public void expire(List<Object> id, Map<GlobalStreamId, Tuple> tuples) {
      for (Tuple tuple : tuples.values()) {
        _collector.fail(tuple);
      }
    }
  }
}