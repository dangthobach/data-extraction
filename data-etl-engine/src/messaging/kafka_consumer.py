"""
Kafka Consumer for ETL Engine
"""
import json
from confluent_kafka import Consumer, KafkaError, KafkaException
import structlog
from typing import Callable, Dict, Any

from config import settings

logger = structlog.get_logger()


class KafkaConsumerService:
    """Kafka consumer for file ready events"""
    
    def __init__(self):
        self.config = {
            'bootstrap.servers': settings.kafka_bootstrap_servers,
            'group.id': settings.kafka_consumer_group,
            'auto.offset.reset': settings.kafka_auto_offset_reset,
            'enable.auto.commit': False,  # Manual commit for reliability
        }
        self.consumer = None
        self.running = False
    
    def start(self, message_handler: Callable[[Dict[str, Any]], None]):
        """
        Start consuming messages
        
        Args:
            message_handler: Callback function to handle each message
        """
        self.consumer = Consumer(self.config)
        self.consumer.subscribe([settings.kafka_topic_file_ready])
        self.running = True
        
        logger.info("kafka_consumer_started", 
                   topic=settings.kafka_topic_file_ready,
                   group=settings.kafka_consumer_group)
        
        try:
            while self.running:
                msg = self.consumer.poll(timeout=1.0)
                
                if msg is None:
                    continue
                
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        logger.debug("kafka_partition_eof", 
                                   partition=msg.partition(), 
                                   offset=msg.offset())
                    else:
                        raise KafkaException(msg.error())
                else:
                    try:
                        # Parse message
                        value = json.loads(msg.value().decode('utf-8'))
                        logger.info("message_received", 
                                   topic=msg.topic(),
                                   partition=msg.partition(),
                                   offset=msg.offset(),
                                   job_id=value.get('jobId'))
                        
                        # Process message
                        message_handler(value)
                        
                        # Commit offset after successful processing
                        self.consumer.commit(msg)
                        
                    except Exception as e:
                        logger.error("message_processing_error",
                                   error=str(e),
                                   offset=msg.offset())
                        # Don't commit - message will be reprocessed
                        
        except KeyboardInterrupt:
            logger.info("kafka_consumer_interrupted")
        finally:
            self.stop()
    
    def stop(self):
        """Stop the consumer"""
        self.running = False
        if self.consumer:
            self.consumer.close()
            logger.info("kafka_consumer_stopped")


# Factory function
def create_consumer() -> KafkaConsumerService:
    return KafkaConsumerService()
